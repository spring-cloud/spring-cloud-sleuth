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

package org.springframework.cloud.sleuth.zipkin;

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
import org.springframework.cloud.sleuth.Trace;
import org.springframework.cloud.sleuth.TraceScope;
import org.springframework.cloud.sleuth.autoconfig.TraceAutoConfiguration;
import org.springframework.cloud.sleuth.event.ClientReceivedEvent;
import org.springframework.cloud.sleuth.event.ClientSentEvent;
import org.springframework.cloud.sleuth.event.ServerReceivedEvent;
import org.springframework.cloud.sleuth.event.ServerSentEvent;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.cloud.sleuth.zipkin.ZipkinSpanListenerTests.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.github.kristofa.brave.EmptySpanCollector;
import com.github.kristofa.brave.SpanCollector;

/**
 * @author Dave Syer
 *
 */
@SpringApplicationConfiguration(classes = TestConfiguration.class)
@RunWith(SpringJUnit4ClassRunner.class)
public class ZipkinSpanListenerTests {

	@Autowired
	private Trace trace;

	@Autowired
	private ApplicationContext application;

	@Autowired
	private ZipkinTestConfiguration test;

	@PostConstruct
	public void init() {
		this.test.spans.clear();
	}

	@Test
	public void acquireAndRelease() {
		TraceScope context = this.trace.startSpan("foo");
		context.close();
		assertEquals(1, this.test.spans.size());
	}

	@Test
	public void rpcAnnotations() {
		Span parent = MilliSpan.builder().traceId("xxxx").name("parent").remote(true).build();
		TraceScope context = this.trace.startSpan("child", parent);
		this.application.publishEvent(new ClientSentEvent(this, context.getSpan()));
		this.application.publishEvent(new ServerReceivedEvent(this, parent, context.getSpan()));
		this.application.publishEvent(new ServerSentEvent(this, parent, context.getSpan()));
		this.application.publishEvent(new ClientReceivedEvent(this, context.getSpan()));
		context.close();
		assertEquals(2, this.test.spans.size());
	}

	@Configuration
	@Import({ ZipkinTestConfiguration.class, ZipkinAutoConfiguration.class, TraceAutoConfiguration.class,
			PropertyPlaceholderAutoConfiguration.class })
	protected static class TestConfiguration {
	}

	@Configuration
	protected static class ZipkinTestConfiguration {

		private List<com.twitter.zipkin.gen.Span> spans = new ArrayList<>();

		@Bean
		public Sampler<?> defaultSampler() {
			return new AlwaysSampler();
		}

		@Bean
		public SpanCollector collector() {
			return new EmptySpanCollector() {
				@Override
				public void collect(com.twitter.zipkin.gen.Span span) {
					ZipkinTestConfiguration.this.spans.add(span);
				}
			};
		}

	}}
