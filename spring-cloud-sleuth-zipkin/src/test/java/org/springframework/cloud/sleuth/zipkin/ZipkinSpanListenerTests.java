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

import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.cloud.sleuth.TraceManager;
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

/**
 * @author Dave Syer
 *
 */
@SpringApplicationConfiguration(classes = TestConfiguration.class)
@RunWith(SpringJUnit4ClassRunner.class)
public class ZipkinSpanListenerTests {

	@Autowired
	private TraceManager traceManager;

	@Autowired
	private ApplicationContext application;

	@Autowired
	private ZipkinTestConfiguration test;

	@Autowired
	private ZipkinSpanListener listener;

	@PostConstruct
	public void init() {
		this.test.spans.clear();
	}

	Span parent = MilliSpan.builder().traceId(1L).name("parent").remote(true).build();

	/** Sleuth timestamps are millisecond granularity while zipkin is microsecond. */
	@Test
	public void convertsTimestampAndDurationToMicroseconds() {
		long start = System.currentTimeMillis();
		parent.log("http/request/retry"); // System.currentTimeMillis

		io.zipkin.Span result = listener.convert(parent);

		assertThat(result.timestamp)
				.isEqualTo(parent.getBegin() * 1000);
		assertThat(result.duration)
				.isEqualTo((parent.getEnd() - parent.getBegin()) * 1000);
		assertThat(result.annotations.get(0).timestamp)
				.isGreaterThanOrEqualTo(start * 1000)
				.isLessThanOrEqualTo(System.currentTimeMillis() * 1000);
	}

	/** Sleuth host corresponds to annotation/binaryAnnotation.host in zipkin. */
	@Test
	public void annotationsIncludeHost() {
		parent.log("http/request/retry");
		parent.tag("spring-boot/version", "1.3.1.RELEASE");

		io.zipkin.Span result = listener.convert(parent);

		assertThat(result.annotations.get(0).endpoint)
				.isEqualTo(listener.localEndpoint);
		assertThat(result.binaryAnnotations.get(0).endpoint)
				.isEqualTo(result.annotations.get(0).endpoint);
	}

	/** zipkin's Endpoint.serviceName should never be null. */
	@Test
	public void localEndpointIncludesServiceName() {
		assertThat(listener.localEndpoint.serviceName)
				.isNotEmpty();
	}

	/**
	 * In zipkin, the service context is attached to annotations. Sleuth spans
	 * that have no annotations will get an "lc" one, which allows them to be
	 * queryable in zipkin by service name.
	 */
	@Test
	public void spanWithoutAnnotationsLogsComponent() {
		Trace context = this.traceManager.startSpan("foo");
		this.traceManager.close(context);
		assertEquals(1, this.test.spans.size());
		assertThat(this.test.spans.get(0).binaryAnnotations.get(0).endpoint.serviceName)
				.isEqualTo("unknown"); // TODO: "unknown" bc process id, documented as not nullable, is null.
	}

	@Test
	public void rpcAnnotations() {
		Trace context = this.traceManager.startSpan("child", parent);
		this.application.publishEvent(new ClientSentEvent(this, context.getSpan()));
		this.application.publishEvent(new ServerReceivedEvent(this, parent, context.getSpan()));
		this.application.publishEvent(new ServerSentEvent(this, parent, context.getSpan()));
		this.application.publishEvent(new ClientReceivedEvent(this, context.getSpan()));
		this.traceManager.close(context);
		assertEquals(2, this.test.spans.size());
	}

	@Configuration
	@Import({ ZipkinTestConfiguration.class, ZipkinAutoConfiguration.class, TraceAutoConfiguration.class,
			PropertyPlaceholderAutoConfiguration.class })
	protected static class TestConfiguration {
	}

	@Configuration
	protected static class ZipkinTestConfiguration {

		private List<io.zipkin.Span> spans = new ArrayList<>();

		@Bean
		public Sampler<?> defaultSampler() {
			return new AlwaysSampler();
		}

		@Bean
		public ZipkinSpanReporter reporter() {
			return this.spans::add;
		}

	}
}
