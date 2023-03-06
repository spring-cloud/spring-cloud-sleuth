/*
 * Copyright 2013-2021 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.kafka;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.reactivestreams.Publisher;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverOptions;
import reactor.kafka.receiver.ReceiverRecord;
import reactor.test.StepVerifier;
import reactor.util.context.ContextView;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.cloud.sleuth.CurrentTraceContext;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.TraceContext;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.exporter.FinishedSpan;
import org.springframework.cloud.sleuth.instrument.reactor.ReactorSleuth;
import org.springframework.cloud.sleuth.propagation.Propagator;
import org.springframework.cloud.sleuth.test.TestSpanHandler;
import org.springframework.cloud.sleuth.test.TestTracingAwareSupplier;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Testcontainers
@ExtendWith(MockitoExtension.class)
@Tag("DockerRequired")
public abstract class KafkaReceiverTest implements TestTracingAwareSupplier {

	static final String HOOK_KEY = "org.springframework.cloud.sleuth.autoconfig.instrument.reactor.TraceReactorAutoConfiguration.TraceReactorConfiguration";

	protected String testTopic;

	protected Tracer tracer = tracerTest().tracing().tracer();

	protected Propagator propagator = tracerTest().tracing().propagator();

	protected TestSpanHandler spans = tracerTest().handler();

	protected CurrentTraceContext currentTraceContext = tracerTest().tracing().currentTraceContext();

	protected Propagator.Getter<ConsumerRecord<?, ?>> extractor = new TracingKafkaPropagatorGetter();

	private Disposable consumerSubscription;

	private Disposable shareableReceiverDisposable;

	protected Flux<ReceiverRecord<String, String>> shareableReceiver;

	protected final AtomicInteger receivedCounter = new AtomicInteger(0);

	AnnotationConfigApplicationContext springContext = new AnnotationConfigApplicationContext();

	@Mock(answer = Answers.RETURNS_DEEP_STUBS)
	BeanFactory beanFactory;

	@Container
	protected static final KafkaContainer kafkaContainer = new KafkaContainer(
			DockerImageName.parse("confluentinc/cp-kafka:6.1.1")).withExposedPorts(9093)
					.waitingFor(Wait.forListeningPort());

	@BeforeAll
	static void setupAll() {
		kafkaContainer.start();
	}

	@AfterAll
	static void destroyAll() {
		kafkaContainer.stop();
	}

	@BeforeEach
	void setup() {
		// We need to enable scope passing to actually see the context downstream
		Hooks.resetOnEachOperator();
		Hooks.resetOnLastOperator();
		Schedulers.resetOnScheduleHooks();

		testTopic = UUID.randomUUID().toString();
		Map<String, Object> consumerProperties = new HashMap<>();
		consumerProperties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers());
		consumerProperties.put(ConsumerConfig.GROUP_ID_CONFIG, "test-consumer-group");
		consumerProperties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
		consumerProperties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
		consumerProperties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
		ReceiverOptions<String, String> options = ReceiverOptions.create(consumerProperties);

		options = options.withKeyDeserializer(new StringDeserializer()).withValueDeserializer(new StringDeserializer())
				.subscription(Collections.singletonList(testTopic));

		KafkaReceiver<String, String> kafkaReceiver = new TracingKafkaReceiver<>(
				new ReactiveKafkaTracingPropagator(tracer, propagator, extractor), KafkaReceiver.create(options));

		// Create shareable receiver
		this.shareableReceiver = kafkaReceiver.receive().publish().autoConnect(0,
				disposable -> this.shareableReceiverDisposable = disposable);

		// Create subscription for previous tests compatibility
		this.consumerSubscription = shareableReceiver.subscribeOn(Schedulers.single())
				.subscribe(record -> receivedCounter.incrementAndGet());

		this.receivedCounter.set(0);
	}

	@AfterEach
	void destroy() {
		springContext.close();
		Hooks.resetOnEachOperator();
		Hooks.resetOnLastOperator();
		Schedulers.resetOnScheduleHooks();

		this.consumerSubscription.dispose();
		if (this.shareableReceiverDisposable != null) {
			this.shareableReceiverDisposable.dispose();
		}
	}

	@Test
	public void should_create_and_finish_consumer_span() {
		KafkaProducer<String, String> kafkaProducer = KafkaTestUtils
				.buildTestKafkaProducer(kafkaContainer.getBootstrapServers());
		ProducerRecord<String, String> producerRecord = new ProducerRecord<>(testTopic, "test", "test");
		kafkaProducer.send(producerRecord);

		await().atMost(Duration.ofSeconds(15)).until(() -> receivedCounter.intValue() == 1);

		BDDAssertions.then(this.tracer.currentSpan()).isNull();
		BDDAssertions.then(this.spans).hasSize(1);
		FinishedSpan span = this.spans.get(0);
		BDDAssertions.then(span.getKind()).isEqualTo(Span.Kind.CONSUMER);
		BDDAssertions.then(span.getTags()).isNotEmpty();
		BDDAssertions.then(span.getTags().get("kafka.topic")).isEqualTo(testTopic);
		BDDAssertions.then(span.getTags().get("kafka.offset")).isEqualTo("0");
		BDDAssertions.then(span.getTags().get("kafka.partition")).isEqualTo("0");
	}

	@Test
	public void should_pass_tracing_context_for_consumers() {
		springContext.registerBean(Tracer.class, () -> this.tracer);
		springContext.registerBean(CurrentTraceContext.class, () -> this.currentTraceContext);
		springContext.refresh();

		Hooks.onEachOperator(HOOK_KEY, ReactorSleuth.onEachOperatorForOnEachInstrumentation(springContext));
		Hooks.onLastOperator(HOOK_KEY, ReactorSleuth.onLastOperatorForOnEachInstrumentation(springContext));

		KafkaProducer<String, String> kafkaProducer = KafkaTestUtils
				.buildTestKafkaProducer(kafkaContainer.getBootstrapServers());
		ProducerRecord<String, String> producerRecord = new ProducerRecord<>(testTopic, "test", "test-with-trace");
		producerRecord.headers().add("b3", "80f198ee56343ba864fe8b2a57d3eff7-e457b5a2e4d86bd1-1".getBytes());
		kafkaProducer.send(producerRecord);

		Publisher<ContextView> testFlux = shareableReceiver.flatMap(record -> Mono.deferContextual(Mono::just));

		StepVerifier.create(testFlux).assertNext(contextView -> {
			TraceContext traceContext = contextView.get(TraceContext.class);

			assertThat(traceContext).returns("80f198ee56343ba864fe8b2a57d3eff7", TraceContext::traceId)
					.returns("e457b5a2e4d86bd1", TraceContext::parentId);
		}).thenCancel().verify(Duration.ofSeconds(15));
	}

	@Override
	public void cleanUpTracing() {
		this.spans.clear();
	}

}
