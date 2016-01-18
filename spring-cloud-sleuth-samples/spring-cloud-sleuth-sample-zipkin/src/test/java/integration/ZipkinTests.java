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

import integration.ZipkinTests.WaitUntilZipkinIsUpConfig;
import io.zipkin.server.ZipkinServer;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.boot.test.WebIntegrationTest;
import org.springframework.cloud.sleuth.zipkin.HttpZipkinSpanReporter;
import org.springframework.cloud.sleuth.zipkin.ZipkinProperties;
import org.springframework.cloud.sleuth.zipkin.ZipkinSpanReporter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import sample.SampleZipkinApplication;
import tools.AbstractIntegrationTest;

import java.util.Random;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = { WaitUntilZipkinIsUpConfig.class,
		SampleZipkinApplication.class })
@WebIntegrationTest
@TestPropertySource(properties = "sample.zipkin.enabled=true")
public class ZipkinTests extends AbstractIntegrationTest {

	private static final String APP_NAME = "testsleuthzipkin";
	private static int port = 3380;
	private static String sampleAppUrl = "http://localhost:" + port;

	@Before
	public void setup() {
		ZipkinServer.main(new String[] { "server.port=9411" });
		await().until(zipkinQueryServerIsUp());
	}

	@Test
	public void should_propagate_spans_to_zipkin() {
		long traceId = new Random().nextLong();

		await().until(httpMessageWithTraceIdInHeadersIsSuccessfullySent(
				sampleAppUrl + "/hi2", traceId));

		await().until(allSpansWereRegisteredInZipkinWithTraceIdEqualTo(traceId));
	}

	@Override
	protected String getAppName() {
		return APP_NAME;
	}

	@Configuration
	@Slf4j
	public static class WaitUntilZipkinIsUpConfig {
		@Bean
		@SneakyThrows
		public ZipkinSpanReporter spanCollector(final ZipkinProperties zipkin) {
			await().until(new Runnable() {
				@Override
				public void run() {
					try {
						WaitUntilZipkinIsUpConfig.this.getSpanCollector(zipkin);
					}
					catch (Exception e) {
						log.error("Exception occurred while trying to connect to zipkin ["
								+ e.getCause() + "]");
						throw new AssertionError(e);
					}
				}
			});
			return getSpanCollector(zipkin);
		}

		private ZipkinSpanReporter getSpanCollector(ZipkinProperties zipkin) {
			return new HttpZipkinSpanReporter(zipkin.getBaseUrl(), zipkin.getFlushInterval());
		}
	}
}
