/*
 * Copyright 2013-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package integration;

import java.net.URI;
import java.util.Random;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.boot.test.WebIntegrationTest;
import org.springframework.cloud.sleuth.metric.SpanMetricReporter;
import org.springframework.cloud.sleuth.zipkin.HttpZipkinSpanReporter;
import org.springframework.cloud.sleuth.zipkin.ZipkinProperties;
import org.springframework.cloud.sleuth.zipkin.ZipkinSpanReporter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.util.SocketUtils;

import integration.ZipkinTests.WaitUntilZipkinIsUpConfig;
import sample.SampleZipkinApplication;
import tools.AbstractIntegrationTest;
import zipkin.server.EnableZipkinServer;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = { WaitUntilZipkinIsUpConfig.class,
		SampleZipkinApplication.class })
@WebIntegrationTest
@TestPropertySource(properties = {"sample.zipkin.enabled=true"})
public class ZipkinTests extends AbstractIntegrationTest {

	private static final String APP_NAME = "testsleuthzipkin";
	@Value("${local.server.port}")
	private int port = 3380;
	private String sampleAppUrl = "http://localhost:" + this.port;
	@Autowired ZipkinProperties zipkinProperties;

	@Before
	public void setup() {
		ZipkinServer.main(new String[] { "--server.port=" + getPortFromProps() });
		await().until(zipkinQueryServerIsUp());
	}

	@Override protected int getZipkinServerPort() {
		return getPortFromProps();
	}

	private int getPortFromProps() {
		return URI.create(this.zipkinProperties.getBaseUrl()).getPort();
	}

	@Test
	public void should_propagate_spans_to_zipkin() {
		long traceId = new Random().nextLong();

		await().until(httpMessageWithTraceIdInHeadersIsSuccessfullySent(
				this.sampleAppUrl + "/hi2", traceId));

		await().until(allSpansWereRegisteredInZipkinWithTraceIdEqualTo(traceId));
	}

	@Override
	protected String getAppName() {
		return APP_NAME;
	}

	@Configuration
	public static class WaitUntilZipkinIsUpConfig {

		private static final Log log = LogFactory.getLog(WaitUntilZipkinIsUpConfig.class);

		@Bean
		@Primary
		ZipkinProperties testZipkinProperties() {
			int freePort = SocketUtils.findAvailableTcpPort();
			ZipkinProperties zipkinProperties = new ZipkinProperties();
			zipkinProperties.setBaseUrl("http://localhost:" + freePort);
			return zipkinProperties;
		}

		@Bean
		public ZipkinSpanReporter spanCollector(final ZipkinProperties zipkin,
				final SpanMetricReporter spanMetricReporter) {
			await().until(new Runnable() {
				@Override
				public void run() {
					try {
						WaitUntilZipkinIsUpConfig.this.getSpanCollector(zipkin,
								spanMetricReporter);
					}
					catch (Exception e) {
						log.error("Exception occurred while trying to connect to zipkin ["
								+ e.getCause() + "]");
						throw new AssertionError(e);
					}
				}
			});
			return getSpanCollector(zipkin, spanMetricReporter);
		}

		private ZipkinSpanReporter getSpanCollector(ZipkinProperties zipkin,
				SpanMetricReporter spanMetricReporter) {
			return new HttpZipkinSpanReporter(zipkin.getBaseUrl(),
					zipkin.isBasicAuthenticated(), zipkin.getUsername(),
					zipkin.getPassword(), zipkin.getFlushInterval(),
					zipkin.getCompression().isEnabled(), spanMetricReporter);
		}
	}

	@SpringBootApplication
	@EnableZipkinServer
	protected static class ZipkinServer {
		public static void main(String[] args) {
			SpringApplication.run(ZipkinServer.class, args);
		}
	}
}
