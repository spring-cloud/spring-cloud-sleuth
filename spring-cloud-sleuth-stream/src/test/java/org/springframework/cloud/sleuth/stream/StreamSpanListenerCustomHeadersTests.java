/*
 * Copyright 2013-2016 the original author or authors.
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

package org.springframework.cloud.sleuth.stream;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import javax.annotation.PostConstruct;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.cloud.sleuth.Sampler;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.autoconfig.TraceAutoConfiguration;
import org.springframework.cloud.sleuth.log.NoOpSpanLogger;
import org.springframework.cloud.sleuth.log.SpanLogger;
import org.springframework.cloud.sleuth.metric.TraceMetricsAutoConfiguration;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.cloud.stream.config.ChannelBindingAutoConfiguration;
import org.springframework.cloud.stream.test.binder.TestSupportBinderAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author Dave Syer
 *
 */
@SpringBootTest(classes = StreamSpanListenerCustomHeadersTests.CustomTestConfiguration.class, webEnvironment = WebEnvironment.NONE)
@TestPropertySource(properties = {"spring.sleuth.integration.headers.traceId=correlationId",
		"spring.sleuth.integration.headers.spanId=spanId",
		"spring.sleuth.integration.headers.parentId=parentId",
		"spring.sleuth.integration.headers.sampled=sampled"})
@RunWith(SpringJUnit4ClassRunner.class)
public class StreamSpanListenerCustomHeadersTests {

	@Autowired Tracer tracer;
	@Autowired ZipkinTestConfiguration test;

	@Autowired
	@Qualifier(SleuthSource.OUTPUT)
	private MessageChannel output;

	@PostConstruct
	public void init() {
		this.test.spans.clear();
	}

	@Test
	public void should_contain_changed_headers() {
		Span context = this.tracer.createSpan("http:foo");
		this.tracer.close(context);

		output.send(MessageBuilder.withPayload("foo").build());

		then(this.test.messages.poll().getHeaders())
				.containsEntry("sampled", "0");
	}

	@Configuration
	@Import({ ZipkinTestConfiguration.class, SleuthStreamAutoConfiguration.class,
			TraceMetricsAutoConfiguration.class, TestSupportBinderAutoConfiguration.class,
			ChannelBindingAutoConfiguration.class, TraceAutoConfiguration.class,
			PropertyPlaceholderAutoConfiguration.class })
	protected static class CustomTestConfiguration {
	}

	@Configuration
	@MessageEndpoint
	protected static class ZipkinTestConfiguration {

		private BlockingQueue<Span> spans = new LinkedBlockingQueue<>();
		private BlockingQueue<Message> messages = new LinkedBlockingQueue<>();

		@Autowired
		StreamSpanReporter listener;

		@ServiceActivator(inputChannel = SleuthSource.OUTPUT)
		public void handle(Message<?> msg) {
			messages.add(msg);
		}

		@Bean
		SpanLogger spanLogger() {
			return new NoOpSpanLogger();
		}

		@Bean
		public Sampler defaultSampler() {
			return new AlwaysSampler();
		}

		@PostConstruct
		public void init() {
			this.listener.setQueue(this.spans);
		}

	}

}
