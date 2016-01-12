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

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.boot.test.WebIntegrationTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.util.JdkIdGenerator;
import org.testcontainers.containers.DockerComposeContainer;
import sample.SampleZipkinApplication;
import tools.AbstractDockerIntegrationTest;

import java.io.File;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = { AbstractDockerIntegrationTest.ZipkinConfig.class, SampleZipkinApplication.class })
@WebIntegrationTest
@TestPropertySource(properties="sample.zipkin.enabled=true")
@Slf4j
@Ignore("Not passing beacuse of 400 from query")
public class ZipkinDockerTests extends AbstractDockerIntegrationTest {

	private static final String APP_NAME = "testsleuthzipkin";
	private static int port = 3380;
	private static String sampleAppUrl = "http://localhost:" + port;

	@ClassRule
	public static DockerComposeContainer environment =
			new DockerComposeContainer(new File("src/test/resources/docker-compose.yml"))
					.withExposedService("rabbitmq_1", 5672)
					.withExposedService("mysql_1", 3306)
					.withExposedService("query_1", 9411);

	@Before
	public void setup() {
		await().until(zipkinQueryServerIsUp());
	}

	@Test
	@SneakyThrows
	public void should_propagate_spans_to_zipkin() {
		String traceId = new JdkIdGenerator().generateId().toString();

		await().until(httpMessageWithTraceIdInHeadersIsSuccessfullySent(sampleAppUrl + "/hi2", traceId));

		await().until(allSpansWereRegisteredInZipkinWithTraceIdEqualTo(traceId));
	}

	@Override
	protected String getAppName() {
		return APP_NAME;
	}
}
