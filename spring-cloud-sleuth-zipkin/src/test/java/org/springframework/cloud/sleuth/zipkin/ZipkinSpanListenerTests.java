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

package org.springframework.cloud.sleuth.zipkin;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.PostConstruct;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.sleuth.Sampler;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanReporter;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.cloud.sleuth.zipkin.ZipkinSpanListenerTests.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.context.junit4.SpringRunner;

import zipkin.Constants;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;

/**
 * @author Dave Syer
 *
 */
@SpringBootTest(classes = TestConfiguration.class)
@RunWith(SpringRunner.class)
public class ZipkinSpanListenerTests {

	@Autowired Tracer tracer;
	@Autowired ApplicationContext application;
	@Autowired TestConfiguration test;
	@Autowired ZipkinSpanListener spanListener;
	@Autowired ZipkinSpanReporter spanReporter;
	@Autowired MockEnvironment mockEnvironment;
	@Autowired EndpointLocator endpointLocator;

	@PostConstruct
	public void init() {
		this.test.zipkinSpans.clear();
	}

	Span parent = Span.builder().traceId(1L).name("http:parent").remote(true).build();

	/** Sleuth timestamps are millisecond granularity while zipkin is microsecond. */
	@Test
	public void convertsTimestampToMicrosecondsAndSetsDurationToAccumulatedMicros() {
		Span span = Span.builder().traceId(1L).name("http:api").build();
		long start = System.currentTimeMillis();
		span.logEvent("hystrix/retry"); // System.currentTimeMillis
		span.stop();

		zipkin.Span result = this.spanListener.convert(span);

		assertThat(result.timestamp)
				.isEqualTo(span.getBegin() * 1000);
		assertThat(result.duration)
				.isEqualTo(span.getAccumulatedMicros());
		assertThat(result.annotations.get(0).timestamp)
				.isGreaterThanOrEqualTo(start * 1000)
				.isLessThanOrEqualTo(System.currentTimeMillis() * 1000);
	}

	@Test
	public void setsTheDurationToTheDifferenceBetweenCRandCS()
			throws InterruptedException {
		Span span = Span.builder().traceId(1L).name("http:api").build();
		span.logEvent(Span.CLIENT_SEND);
		Thread.sleep(10);
		span.logEvent(Span.CLIENT_RECV);
		Thread.sleep(20);
		span.stop();

		zipkin.Span result = this.spanListener.convert(span);

		assertThat(result.timestamp)
				.isEqualTo(span.getBegin() * 1000);
		long clientSendTimestamp = span.logs().stream().filter(log -> Span.CLIENT_SEND.equals(log.getEvent()))
				.findFirst().get().getTimestamp();
		long clientRecvTimestamp = span.logs().stream().filter(log -> Span.CLIENT_RECV.equals(log.getEvent()))
				.findFirst().get().getTimestamp();
		assertThat(result.duration)
				.isNotEqualTo(span.getAccumulatedMicros())
				.isEqualTo((clientRecvTimestamp - clientSendTimestamp) * 1000);
	}

	/** Zipkin's duration should only be set when the span is finished. */
	@Test
	public void doesntSetDurationWhenStillRunning() {
		Span span = Span.builder().traceId(1L).name("http:api").build();
		zipkin.Span result = this.spanListener.convert(span);

		assertThat(result.timestamp)
				.isGreaterThan(0); // sanity check it did start
		assertThat(result.duration)
				.isNull();
	}

	/**
	 * In the RPC span model, the client owns the timestamp and duration of the span. If we
	 * were propagated an id, we can assume that we shouldn't report timestamp or duration,
	 * rather let the client do that. Worst case we were propagated an unreported ID and
	 * Zipkin backfills timestamp and duration.
	 */
	@Test
	public void doesntSetTimestampOrDurationWhenRemote() {
		this.parent.stop();
		zipkin.Span result = this.spanListener.convert(this.parent);

		assertThat(result.timestamp)
				.isNull();
		assertThat(result.duration)
				.isNull();
	}

	/** Sleuth host corresponds to annotation/binaryAnnotation.host in zipkin. */
	@Test
	public void annotationsIncludeHost() {
		this.parent.logEvent("hystrix/retry");
		this.parent.tag("spring-boot/version", "1.3.1.RELEASE");

		zipkin.Span result = this.spanListener.convert(this.parent);

		assertThat(result.annotations.get(0).endpoint)
				.isEqualTo(this.spanListener.endpointLocator.local());
		assertThat(result.binaryAnnotations.get(0).endpoint)
				.isEqualTo(result.annotations.get(0).endpoint);
	}

	/** zipkin's Endpoint.serviceName should never be null. */
	@Test
	public void localEndpointIncludesServiceName() {
		assertThat(this.spanListener.endpointLocator.local().serviceName)
				.isNotEmpty();
	}

	/**
	 * In zipkin, the service context is attached to annotations. Sleuth spans
	 * that have no annotations will get an "lc" one, which allows them to be
	 * queryable in zipkin by service name.
	 */
	@Test
	public void spanWithoutAnnotationsLogsComponent() {
		Span context = this.tracer.createSpan("http:foo");
		this.tracer.close(context);
		assertEquals(1, this.test.zipkinSpans.size());
		assertThat(this.test.zipkinSpans.get(0).binaryAnnotations.get(0).value)
				.isEqualTo("unknown".getBytes()); // TODO: "unknown" bc process id, documented as not nullable, is null.
	}

	@Test
	public void rpcAnnotations() {
		Span context = this.tracer.createSpan("http:child", this.parent);
		context.logEvent(Span.CLIENT_SEND);
		logServerReceived(this.parent);
		logServerSent(this.spanListener, this.parent);
		this.tracer.close(context);
		assertEquals(2, this.test.zipkinSpans.size());
	}

	void logServerReceived(Span parent) {
		if (parent != null && parent.isRemote()) {
			parent.logEvent(Span.SERVER_RECV);
		}
	}

	void logServerSent(SpanReporter spanReporter, Span parent) {
		if (parent != null && parent.isRemote()) {
			parent.logEvent(Span.SERVER_SEND);
			spanReporter.report(parent);
		}
	}

	@Test
	public void appendsLocalComponentTagIfNoZipkinLogIsPresent() {
		this.parent.logEvent("hystrix/retry");
		this.parent.stop();

		zipkin.Span result = this.spanListener.convert(this.parent);

		assertThat(result.binaryAnnotations)
				.extracting(input -> input.key)
				.contains(Constants.LOCAL_COMPONENT);
	}

	@Test
	public void appendServerAddressTagIfClientLogIsPresentWhenPeerServiceIsPresent() {
		this.parent.logEvent(Constants.CLIENT_SEND);
		this.parent.tag(Span.SPAN_PEER_SERVICE_TAG_NAME, "fooservice");
		this.parent.stop();

		zipkin.Span result = this.spanListener.convert(this.parent);

		assertThat(result.binaryAnnotations)
				.filteredOn("key", Constants.SERVER_ADDR)
				.isNotEmpty();
	}

	@Test
	public void doesNotAppendServerAddressTagIfClientLogIsPresent() {
		this.parent.logEvent(Constants.CLIENT_SEND);
		this.parent.stop();

		zipkin.Span result = this.spanListener.convert(this.parent);

		assertThat(result.binaryAnnotations)
				.filteredOn("key", Constants.SERVER_ADDR)
				.isEmpty();
	}

	@Test
	public void converts128BitTraceId() {
		Span span = Span.builder().traceIdHigh(1L).traceId(2L).spanId(3L).name("foo").build();

		zipkin.Span result = this.spanListener.convert(span);

		assertThat(result.traceIdHigh).isEqualTo(span.getTraceIdHigh());
		assertThat(result.traceId).isEqualTo(span.getTraceId());
	}

	@Test
	public void shouldReuseServerAddressTag() {
		this.parent.logEvent(Constants.CLIENT_SEND);
		this.parent.tag(Span.SPAN_PEER_SERVICE_TAG_NAME, "fooservice");
		this.parent.stop();

		zipkin.Span result = this.spanListener.convert(this.parent);

		assertThat(result.binaryAnnotations)
				.filteredOn("key", Constants.SERVER_ADDR)
				.extracting(input -> input.endpoint.serviceName)
				.containsOnly("fooservice");
	}

	@Test
	public void shouldNotReportToZipkinWhenSpanIsNotExportable() {
		Span span = Span.builder().exportable(false).build();

		this.spanListener.report(span);

		assertThat(this.test.zipkinSpans).isEmpty();
	}

	@Test
	public void shouldAddClientServiceIdTagWhenSpanContainsRpcEvent() {
		this.parent.logEvent(Span.CLIENT_SEND);
		this.mockEnvironment.setProperty("vcap.application.instance_id", "foo");

		zipkin.Span result = this.spanListener.convert(this.parent);

		assertThat(result.binaryAnnotations)
				.filteredOn("key", Span.INSTANCEID)
				.extracting(input -> input.value)
				.containsOnly("foo".getBytes());
	}

	@Test
	public void shouldNotAddAnyServiceIdTagWhenSpanContainsRpcEventAndThereIsNoEnvironment() {
		this.parent.logEvent(Span.CLIENT_RECV);
		ZipkinSpanListener spanListener = new ZipkinSpanListener(this.spanReporter,
				this.endpointLocator, null);

		zipkin.Span result = spanListener.convert(this.parent);

		assertThat(result.binaryAnnotations)
				.filteredOn("key", Span.INSTANCEID)
				.extracting(input -> input.value)
				.isEmpty();
	}

	@Configuration
	@EnableAutoConfiguration
	protected static class TestConfiguration {

		private List<zipkin.Span> zipkinSpans = new ArrayList<>();

		@Bean
		public Sampler sampler() {
			return new AlwaysSampler();
		}

		@Bean
		public ZipkinSpanReporter reporter() {
			return this.zipkinSpans::add;
		}

		@Bean @Primary MockEnvironment mockEnvironment() {
			return new MockEnvironment();
		}

	}

}
