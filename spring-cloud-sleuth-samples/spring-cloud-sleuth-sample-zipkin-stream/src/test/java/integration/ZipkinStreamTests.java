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

import example.ZipkinStreamServerApplication;
import lombok.SneakyThrows;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.boot.test.WebIntegrationTest;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.stream.Host;
import org.springframework.cloud.sleuth.stream.SleuthSink;
import org.springframework.cloud.sleuth.stream.Spans;
import org.springframework.cloud.stream.test.binder.TestSupportBinderAutoConfiguration;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.MessageChannel;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import tools.AbstractIntegrationTest;

import java.util.Collections;
import java.util.Random;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = { TestSupportBinderAutoConfiguration.class,
		ZipkinStreamServerApplication.class })
@WebIntegrationTest({ "server.port=0", "management.health.rabbit.enabled=false" })
@ActiveProfiles("test")
public class ZipkinStreamTests extends AbstractIntegrationTest {

	@Value("${local.server.port}")
	private int zipkinServerPort = 9411;

	@Autowired
	@Qualifier(SleuthSink.INPUT)
	private MessageChannel input;

	@Test
	@SneakyThrows
	public void should_propagate_spans_to_zipkin() {

		await().until(zipkinServerIsUp());

		long traceId = new Random().nextLong();
		Span span = Span.builder().traceId(traceId).spanId(traceId).name("test")
				.build();
		span.tag(getRequiredBinaryAnnotationName(), "10131");

		this.input.send(MessageBuilder.withPayload(
				new Spans(new Host(getAppName(), "127.0.0.1", 8080), Collections.singletonList(span)))
				.build());

		await().until(allSpansWereRegisteredInZipkinWithTraceIdEqualTo(traceId));
	}

	@Override
	protected int getZipkinServerPort() {
		return this.zipkinServerPort;
	}

	@Override
	protected String getAppName() {
		return "local";
	}

}
