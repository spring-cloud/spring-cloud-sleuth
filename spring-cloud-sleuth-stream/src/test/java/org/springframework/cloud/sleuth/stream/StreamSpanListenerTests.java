/*
 * Copyright 2015 the original author or authors.
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

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.PostConstruct;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.cloud.sleuth.MilliSpan;
import org.springframework.cloud.sleuth.Sampler;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.autoconfig.TraceAutoConfiguration;
import org.springframework.cloud.sleuth.event.ClientReceivedEvent;
import org.springframework.cloud.sleuth.event.ClientSentEvent;
import org.springframework.cloud.sleuth.event.ServerReceivedEvent;
import org.springframework.cloud.sleuth.event.ServerSentEvent;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.cloud.sleuth.stream.StreamSpanListenerTests.TestConfiguration;
import org.springframework.cloud.stream.config.ChannelBindingAutoConfiguration;
import org.springframework.cloud.stream.test.binder.TestSupportBinderAutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * @author Dave Syer
 *
 */
@SpringApplicationConfiguration(classes = TestConfiguration.class)
@RunWith(SpringJUnit4ClassRunner.class)
public class StreamSpanListenerTests {

	@Autowired
	private Tracer tracer;

	@Autowired
	private ApplicationContext application;

	@Autowired
	private ZipkinTestConfiguration test;

	@Autowired
	StreamSpanListener listener;

	@PostConstruct
	public void init() {
		this.test.spans.clear();
	}

	@Test
	public void acquireAndRelease() {
		Span context = this.tracer.startTrace("foo");
		this.tracer.close(context);
		assertEquals(1, this.test.spans.size());
	}

	@Test
	public void rpcAnnotations() {
		Span parent = MilliSpan.builder().traceId(1L).name("parent").remote(true)
				.build();
		Span context = this.tracer.joinTrace("child", parent);
		this.application.publishEvent(new ClientSentEvent(this, context));
		this.application
				.publishEvent(new ServerReceivedEvent(this, parent, context));
		this.application
				.publishEvent(new ServerSentEvent(this, parent, context));
		this.application.publishEvent(new ClientReceivedEvent(this, context));
		this.tracer.close(context);
		assertEquals(2, this.test.spans.size());
	}

	@Test
	public void nullSpanName() {
		Span context = this.tracer.startTrace(null, (Sampler) null);
		this.application.publishEvent(new ClientSentEvent(this, context));
		this.tracer.close(context);
		assertEquals(1, this.test.spans.size());
		this.listener.poll();
		assertEquals(0, this.test.spans.size());
	}

	@Configuration
	@Import({ ZipkinTestConfiguration.class, SleuthStreamAutoConfiguration.class,
			TestSupportBinderAutoConfiguration.class, ChannelBindingAutoConfiguration.class,
			TraceAutoConfiguration.class, PropertyPlaceholderAutoConfiguration.class })
	protected static class TestConfiguration {
	}

	@Configuration
	@MessageEndpoint
	protected static class ZipkinTestConfiguration {

		private List<Span> spans = new ArrayList<>();

		@Autowired
		StreamSpanListener listener;

		@ServiceActivator(inputChannel=SleuthSource.OUTPUT)
		public void handle(Message<?> msg) {
		}

		@Bean
		public Sampler<?> defaultSampler() {
			return new AlwaysSampler();
		}

		@PostConstruct
		public void init() {
			this.listener.setQueue(this.spans);
		}

	}

}
