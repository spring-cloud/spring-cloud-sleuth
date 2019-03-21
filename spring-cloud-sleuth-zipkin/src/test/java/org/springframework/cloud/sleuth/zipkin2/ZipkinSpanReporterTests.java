/*
 * Copyright 2013-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.sleuth.zipkin2;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.annotation.PostConstruct;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.sleuth.Sampler;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanAdjuster;
import org.springframework.cloud.sleuth.SpanReporter;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.cloud.sleuth.zipkin2.ZipkinSpanReporterTests.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.context.junit4.SpringRunner;
import zipkin2.Endpoint;
import zipkin2.reporter.Reporter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.BDDAssertions.then;
import static org.junit.Assert.assertEquals;

/**
 * @author Dave Syer
 *
 */
@SpringBootTest(classes = TestConfiguration.class)
@RunWith(SpringRunner.class)
public class ZipkinSpanReporterTests {

	@Autowired Tracer tracer;
	@Autowired TestConfiguration test;
	@Autowired ZipkinSpanReporter spanReporter;
	@Autowired Reporter<zipkin2.Span> zipkinReporter;
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

		zipkin2.Span result = this.spanReporter.convert(span);

		assertThat(result.timestamp())
				.isEqualTo(span.getBegin() * 1000);
		assertThat(result.duration())
				.isEqualTo(span.getAccumulatedMicros());
		assertThat(result.annotations().get(0).timestamp())
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

		zipkin2.Span result = this.spanReporter.convert(span);

		assertThat(result.timestamp()).isEqualTo(span.getBegin() * 1000);
		long clientSendTimestamp = span.logs().stream()
				.filter(log -> Span.CLIENT_SEND.equals(log.getEvent())).findFirst().get()
				.getTimestamp();
		long clientRecvTimestamp = span.logs().stream()
				.filter(log -> Span.CLIENT_RECV.equals(log.getEvent())).findFirst().get()
				.getTimestamp();
		assertThat(result.duration()).isNotEqualTo(span.getAccumulatedMicros())
				.isEqualTo((clientRecvTimestamp - clientSendTimestamp) * 1000);
	}

	/** Zipkin's duration should only be set when the span is finished. */
	@Test
	public void doesntSetDurationWhenStillRunning() {
		Span span = Span.builder().traceId(1L).name("http:api").build();
		zipkin2.Span result = this.spanReporter.convert(span);

		assertThat(result.timestamp())
				.isGreaterThan(0); // sanity check it did start
		assertThat(result.duration())
				.isNull();
	}

	/** Sleuth host corresponds to localEndpoint in zipkin. */
	@Test
	public void spanIncludesLocalEndpoint() {
		this.parent.logEvent("hystrix/retry");
		this.parent.tag("spring-boot/version", "1.3.1.RELEASE");

		zipkin2.Span result = this.spanReporter.convert(this.parent);

		assertThat(result.localEndpoint())
				.isEqualTo(this.spanReporter.endpointLocator.local());
	}

	/** zipkin's Endpoint.serviceName should never be null. */
	@Test
	public void localEndpointIncludesServiceName() {
		assertThat(this.spanReporter.endpointLocator.local().serviceName())
				.isNotEmpty();
	}

	@Test
	public void spanWithoutAnnotationsStillHasEndpoint() {
		Span context = this.tracer.createSpan("http:foo");
		this.tracer.close(context);
		assertEquals(1, this.test.zipkinSpans.size());
		assertThat(this.test.zipkinSpans.get(0).localEndpoint())
				.isNotNull();
	}

	@Test
	public void rpcAnnotations() {
		Span context = this.tracer.createSpan("http:child", this.parent);
		context.logEvent(Span.CLIENT_SEND);
		logServerReceived(this.parent);
		logServerSent(this.spanReporter, this.parent);
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
	public void localComponentNotNeeded() {
		this.parent.logEvent("hystrix/retry");
		this.parent.stop();

		zipkin2.Span result = this.spanReporter.convert(this.parent);

		assertThat(result.tags())
				.isEmpty();
	}

	@Test
	public void addsRemoteEndpointWhenClientLogIsPresentAndPeerServiceIsPresent() {
		this.parent.logEvent("cs");
		this.parent.tag(Span.SPAN_PEER_SERVICE_TAG_NAME, "fooservice");
		this.parent.stop();

		zipkin2.Span result = this.spanReporter.convert(this.parent);

		assertThat(result.remoteEndpoint())
				.isEqualTo(Endpoint.newBuilder().serviceName("fooservice").build());
	}

	@Test
	public void doesNotAddRemoteEndpointTagIfClientLogIsPresent() {
		this.parent.logEvent("cs");
		this.parent.stop();

		zipkin2.Span result = this.spanReporter.convert(this.parent);

		assertThat(result.remoteEndpoint())
				.isNull();
	}

	@Test
	public void converts128BitTraceId() {
		Span span = Span.builder().traceIdHigh(1L).traceId(2L).spanId(3L).name("foo").build();

		zipkin2.Span result = this.spanReporter.convert(span);

		assertThat(result.traceId())
				.isEqualTo("00000000000000010000000000000002");
	}

	@Test
	public void shouldReuseServerAddressTag() {
		this.parent.logEvent("cs");
		this.parent.tag(Span.SPAN_PEER_SERVICE_TAG_NAME, "fooservice");
		this.parent.stop();

		zipkin2.Span result = this.spanReporter.convert(this.parent);

		assertThat(result.remoteEndpoint())
				.isEqualTo(Endpoint.newBuilder().serviceName("fooservice").build());
	}

	@Test
	public void shouldNotReportToZipkinWhenSpanIsNotExportable() {
		Span span = Span.builder().exportable(false).build();

		this.spanReporter.report(span);

		assertThat(this.test.zipkinSpans).isEmpty();
	}

	@Test
	public void shouldAddClientServiceIdTagWhenSpanContainsRpcEvent() {
		this.parent.logEvent(Span.CLIENT_SEND);
		this.mockEnvironment.setProperty("vcap.application.instance_id", "foo");

		zipkin2.Span result = this.spanReporter.convert(this.parent);

		assertThat(result.tags())
				.containsExactly(entry(Span.INSTANCEID, "foo"));
	}

	@Test
	public void shouldNotAddAnyServiceIdTagWhenSpanContainsRpcEventAndThereIsNoEnvironment() {
		this.parent.logEvent(Span.CLIENT_RECV);
		ZipkinSpanReporter spanListener = new ZipkinSpanReporter(this.zipkinReporter,
				this.endpointLocator, null, new ArrayList<>());

		zipkin2.Span result = spanListener.convert(this.parent);

		assertThat(result.tags())
				.isEmpty();
	}

	@Test
	public void should_adjust_span_before_reporting_it() {
		this.parent.logEvent(Span.CLIENT_RECV);
		ZipkinSpanReporter spanListener = new ZipkinSpanReporter(this.zipkinReporter,
				this.endpointLocator, null, Arrays.asList(
						span -> Span.builder().from(span).name("foo").build(),
						span -> Span.builder().from(span).name(span.getName() + "bar").build())) {
			@Override String defaultInstanceId() {
				return "foobar";
			}
		};

		zipkin2.Span result = spanListener.convert(this.parent);

		assertThat(result.name()).isEqualTo("foobar");
	}

	/** Zipkin will take care of processing the shared flag wrt timestamp authority */
	@Test
	public void setsSharedFlag() {
		Span span = Span.builder()
				.name("foo")
				.exportable(false)
				.remote(false)
				.shared(true)
				.build();
		span.stop();

		zipkin2.Span result = this.spanReporter.convert(span);

		assertThat(result.duration()).isNotNull();
		assertThat(result.timestamp()).isNotNull();
		assertThat(result.shared()).isTrue();
	}

	@Test
	public void should_create_server_span() {
		Span span = tracer.createSpan("get");
		span.logEvent("sr");
		span.logEvent("ss");
		span.stop();

		zipkin2.Span result = this.spanReporter.convert(span);

		then(result.kind()).isEqualTo(zipkin2.Span.Kind.SERVER);
		then(result.annotations()).isEmpty();
	}

	@Test
	public void should_create_client_span() {
		Span span = tracer.createSpan("redis");
		span.logEvent("cs");
		span.logEvent("cr");
		span.stop();

		zipkin2.Span result = this.spanReporter.convert(span);

		then(result.kind()).isEqualTo(zipkin2.Span.Kind.CLIENT);
		then(result.annotations()).isEmpty();
	}

	@Test
	public void should_create_produceer_span() {
		Span span = tracer.createSpan("produce");
		span.logEvent("ms");
		span.stop();

		zipkin2.Span result = this.spanReporter.convert(span);

		then(result.kind()).isEqualTo(zipkin2.Span.Kind.PRODUCER);
		then(result.annotations()).isEmpty();
	}

	@Test
	public void should_create_consumer_span() {
		Span span = tracer.createSpan("consume");
		span.logEvent("mr");
		span.stop();

		zipkin2.Span result = this.spanReporter.convert(span);

		then(result.kind()).isEqualTo(zipkin2.Span.Kind.CONSUMER);
		then(result.annotations()).isEmpty();
	}

	@Test
	public void should_change_the_service_name_in_zipkin_to_the_manually_provided_one() {
		// tag::service_name[]
		Span span = tracer.createSpan("redis");
		try {
			span.tag("redis.op", "get");
			span.tag("lc", "redis");
			span.logEvent("cs");
			// call redis service e.g
			// return (SomeObj) redisTemplate.opsForHash().get("MYHASH", someObjKey);
		} finally {
			span.tag("peer.service", "redis");
			span.tag("peer.ipv4", "1.2.3.4");
			span.tag("peer.port", "1234");
			span.logEvent("cr");
			span.stop();
		}
		// end::service_name[]

		zipkin2.Span result = this.spanReporter.convert(span);

		then(result.remoteEndpoint())
				.isEqualTo(Endpoint.newBuilder().serviceName("redis").ip("1.2.3.4").port(1234).build());
		then(result.tags())
				.doesNotContainKeys("peer.service", "peer.ipv4", "peer.port");
	}

	@Configuration
	@EnableAutoConfiguration
	protected static class TestConfiguration {

		private List<zipkin2.Span> zipkinSpans = new ArrayList<>();

		@Bean
		public Sampler sampler() {
			return new AlwaysSampler();
		}

		@Bean
		public Reporter<zipkin2.Span> reporter() {
			return this.zipkinSpans::add;
		}

		@Bean @Primary MockEnvironment mockEnvironment() {
			return new MockEnvironment();
		}

	}

}