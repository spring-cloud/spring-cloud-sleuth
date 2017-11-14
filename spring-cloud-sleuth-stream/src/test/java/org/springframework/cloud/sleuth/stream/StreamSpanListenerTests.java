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

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import javax.annotation.PostConstruct;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.awaitility.Awaitility;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.metrics.export.simple.SimpleProperties;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.cloud.commons.util.UtilAutoConfiguration;
import org.springframework.cloud.sleuth.Sampler;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanReporter;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.autoconfig.TraceAutoConfiguration;
import org.springframework.cloud.sleuth.log.NoOpSpanLogger;
import org.springframework.cloud.sleuth.log.SpanLogger;
import org.springframework.cloud.sleuth.metric.TraceMetricsAutoConfiguration;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.BDDAssertions.then;
import static org.junit.Assert.assertEquals;

/**
 * @author Dave Syer
 *
 */
@SpringBootTest(classes = TestConfiguration.class, webEnvironment = WebEnvironment.NONE)
@RunWith(SpringJUnit4ClassRunner.class)
public class StreamSpanListenerTests {

	@Autowired
	Tracer tracer;
	@Autowired
	ApplicationContext application;
	@Autowired
	ZipkinTestConfiguration test;
	@Autowired
	StreamSpanReporter listener;
	@Autowired
	SpanReporter spanReporter;
	@Autowired
	MeterRegistry meterRegistry;

	@Before
	public void init() {
		this.test.clear();
	}

	@Test
	public void acquireAndRelease() {
		Span context = this.tracer.createSpan("http:foo");

		this.tracer.close(context);

		Awaitility.await().untilAsserted(() -> assertThat(StreamSpanListenerTests.this.test.spans()).hasSize(1));
	}

	@Test
	public void rpcAnnotations() {
		Span parent = Span.builder().traceId(1L).name("http:parent").remote(true)
				.build();
		Span context = this.tracer.createSpan("http:child", parent);
		context.logEvent(Span.CLIENT_SEND);
		logServerReceived(parent);
		logServerSent(this.spanReporter, parent);

		this.tracer.close(context);

		Awaitility.await().untilAsserted(() -> assertThat(StreamSpanListenerTests.this.test.spans()).hasSize(2));
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
	public void nullSpanName() {
		Span span = this.tracer.createSpan(null);
		span.logEvent(Span.CLIENT_SEND);
		this.tracer.close(span);
		assertEquals(1, this.test.spans.size());
		this.listener.poll();
		assertEquals(0, this.test.spans.size());
	}

	@Test
	public void shouldIncreaseNumberOfAcceptedSpans() {
		Span context = this.tracer.createSpan("http:foo");
		this.tracer.close(context);
		this.listener.poll();

		Optional<Counter> counter = this.meterRegistry.find("counter.span.accepted")
				.counter();
		then(counter.isPresent()).isTrue();
		then(counter.get().count()).isGreaterThan(0d);
	}

	@Test
	public void shouldNotReportToZipkinWhenSpanIsNotExportable() {
		Span span = Span.builder().exportable(false).build();

		this.spanReporter.report(span);

		assertThat(this.test.spans).isEmpty();
	}

	@Test
	public void shouldReportToZipkinWhenSpanIsExportable() {
		Span span = Span.builder().exportable(true).build();

		this.spanReporter.report(span);

		assertThat(this.test.spans).isNotEmpty();
	}

	@Configuration
	@Import({ ZipkinTestConfiguration.class, SleuthStreamAutoConfiguration.class,
			TraceMetricsAutoConfiguration.class, TestSupportBinderAutoConfiguration.class,
			ChannelBindingAutoConfiguration.class, TraceAutoConfiguration.class,
			PropertyPlaceholderAutoConfiguration.class, UtilAutoConfiguration.class })
	protected static class TestConfiguration {
	}

	@Configuration
	@MessageEndpoint
	protected static class ZipkinTestConfiguration {

		private BlockingQueue<Span> copyOfSpans = new LinkedBlockingQueue<>();

		private BlockingQueue<Span> spans = new LinkedBlockingQueue<Span>() {
			@Override public int drainTo(Collection<? super Span> c) {
				ZipkinTestConfiguration.this.copyOfSpans.addAll(this);
				return super.drainTo(c);
			}
		};

		void clear() {
			this.spans.clear();
			this.copyOfSpans.clear();
		}

		BlockingQueue<Span> spans() {
			return this.copyOfSpans;
		}

		@Autowired
		StreamSpanReporter listener;

		@ServiceActivator(inputChannel = SleuthSource.OUTPUT)
		public void handle(Message<?> msg) {
		}

		@Bean
		SpanLogger spanLogger() {
			return new NoOpSpanLogger();
		}

		@Bean
		SimpleProperties simpleProperties() {
			SimpleProperties simpleProperties = new SimpleProperties();
			simpleProperties.setStep(Duration.of(60, ChronoUnit.SECONDS));
			return simpleProperties;
		}

		@Bean
		public Sampler defaultSampler() {
			return new AlwaysSampler();
		}

		@Bean
		public MeterRegistry testMeterRegistry() {
			return new SimpleMeterRegistry();
		}

		@PostConstruct
		public void init() {
			this.listener.setQueue(this.spans);
		}

	}

}
