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

import java.util.Collections;
import java.util.Random;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.stream.Host;
import org.springframework.cloud.sleuth.stream.SleuthSink;
import org.springframework.cloud.sleuth.stream.Spans;
import org.springframework.cloud.stream.test.binder.TestSupportBinderAutoConfiguration;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import example.ZipkinStreamServerApplication;
import tools.AbstractIntegrationTest;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = { TestSupportBinderAutoConfiguration.class, ZipkinStreamServerApplication.class },
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = { "management.health.rabbit.enabled=false" })
@ActiveProfiles("test")
public class ZipkinStreamTests extends AbstractIntegrationTest {

	@Value("${local.server.port}")
	private int zipkinServerPort = 9411;
	long traceId = new Random().nextLong();
	long spanId = new Random().nextLong();

	@Autowired
	@Qualifier(SleuthSink.INPUT)
	private MessageChannel input;

	@Before
	public void setup() {
		await().until(zipkinServerIsUp());
	}

	@Test
	public void should_propagate_spans_to_zipkin() {
		Span span = Span.builder().traceId(this.traceId).spanId(this.spanId).name("http:test").build();
		span.tag(getRequiredBinaryAnnotationName(), "10131");

		this.input.send(messageWithSpan(span));

		await().until(allSpansWereRegisteredInZipkinWithTraceIdEqualTo(this.traceId));
	}

	private Message<Spans> messageWithSpan(Span span) {
		return MessageBuilder.withPayload(
				new Spans(new Host(getAppName(), "127.0.0.1", 8080), Collections.singletonList(span)))
				.build();
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
