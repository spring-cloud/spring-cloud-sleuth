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

import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.sleuth.zipkin.ZipkinProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.junit4.SpringRunner;

import integration.ZipkinTests.WaitUntilZipkinIsUpConfig;
import sample.SampleZipkinApplication;
import tools.AbstractIntegrationTest;
import zipkin.junit.ZipkinRule;
import zipkin.server.EnableZipkinServer;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = { WaitUntilZipkinIsUpConfig.class,
		SampleZipkinApplication.class },
		properties = {"sample.zipkin.enabled=true"},
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ZipkinTests extends AbstractIntegrationTest {

	@ClassRule public static final ZipkinRule zipkin = new ZipkinRule();

	private static final String APP_NAME = "testsleuthzipkin";
	@Value("${local.server.port}")
	private int port = 3380;
	private String sampleAppUrl = "http://localhost:" + this.port;
	@Autowired ZipkinProperties zipkinProperties;

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

		@Bean
		@Primary
		ZipkinProperties testZipkinProperties() {
			ZipkinProperties zipkinProperties = new ZipkinProperties();
			zipkinProperties.setBaseUrl(zipkin.httpUrl());
			return zipkinProperties;
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
