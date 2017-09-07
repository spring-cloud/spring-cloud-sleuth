package org.springframework.cloud.sleuth.instrument.reactor;

import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.awaitility.Awaitility;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.sleuth.Sampler;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.cloud.sleuth.util.ExceptionUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit4.SpringRunner;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import static org.assertj.core.api.BDDAssertions.then;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = SpanSubscriberTests.Config.class,
		webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class SpanSubscriberTests {

	private static final Log log = LogFactory.getLog(SpanSubscriberTests.class);

	@Autowired Tracer tracer;

	@Before
	public void setup() {
		ExceptionUtils.setFail(true);
	}

	@Test public void should_pass_tracing_info_when_using_reactor() {
		Span span = this.tracer.createSpan("foo");
		final AtomicReference<Span> spanInOperation = new AtomicReference<>();
		Publisher<Integer> traced = Flux.just(1, 2, 3);
		log.info("Hello");

		Flux.from(traced)
				.map( d -> d + 1)
				.map( d -> d + 1)
				.map( (d) -> {
					spanInOperation.set(SpanSubscriberTests.this.tracer.getCurrentSpan());
					return d + 1;
				})
				.map( d -> d + 1)
				.subscribe(System.out::println);

		then(this.tracer.getCurrentSpan()).isNull();
		then(spanInOperation.get().getParents().get(0)).isEqualTo(span.getSpanId());
		then(ExceptionUtils.getLastException()).isNull();
	}

	@Test public void should_support_reactor_fusion_optimization() {
		Span span = this.tracer.createSpan("foo");
		final AtomicReference<Span> spanInOperation = new AtomicReference<>();
		log.info("Hello");

		Mono.just(1)
		    .flatMap( d -> Flux.just(d + 1).collectList().map(p -> p.get(0)))
		    .map( d -> d + 1)
		    .map( (d) -> {
					spanInOperation.set(SpanSubscriberTests.this.tracer.getCurrentSpan());
					return d + 1;
				})
		    .map( d -> d + 1)
		    .subscribe(System.out::println);

		then(this.tracer.getCurrentSpan()).isNull();
		then(spanInOperation.get().getParents().get(0)).isEqualTo(span.getSpanId());
		then(ExceptionUtils.getLastException()).isNull();
	}

	@Test public void should_not_trace_scalar_flows() {
		this.tracer.createSpan("foo");
		final AtomicReference<Subscription> spanInOperation = new AtomicReference<>();
		log.info("Hello");

		Mono.just(1)
		    .subscribe(new BaseSubscriber<Integer>() {
			    @Override
			    protected void hookOnSubscribe(Subscription subscription) {
				    spanInOperation.set(subscription);
			    }
		    });

		then(this.tracer.getCurrentSpan()).isNotNull();
		then(spanInOperation.get()).isNotInstanceOf(SpanSubscriber.class);

		Mono.<Integer>error(new Exception())
		    .subscribe(new BaseSubscriber<Integer>() {
			    @Override
			    protected void hookOnSubscribe(Subscription subscription) {
				    spanInOperation.set(subscription);
			    }

			    @Override
			    protected void hookOnError(Throwable throwable) {
			    }
		    });

		then(this.tracer.getCurrentSpan()).isNotNull();
		then(spanInOperation.get()).isNotInstanceOf(SpanSubscriber.class);

		Mono.<Integer>empty()
		    .subscribe(new BaseSubscriber<Integer>() {
			    @Override
			    protected void hookOnSubscribe(Subscription subscription) {
				    spanInOperation.set(subscription);
			    }
		    });

		then(this.tracer.getCurrentSpan()).isNotNull();
		then(spanInOperation.get()).isNotInstanceOf(SpanSubscriber.class);
		then(ExceptionUtils.getLastException()).isNull();
	}

	@Test
	public void should_pass_tracing_info_when_using_reactor_async() {

		Span span = this.tracer.createSpan("foo");
		final AtomicReference<Span> spanInOperation = new AtomicReference<>();
		log.info("Hello");

		Flux.just(1, 2, 3)
				.publishOn(Schedulers.single())
				.log("reactor.1")
				.map( d -> d + 1)
				.map( d -> d + 1)
				.publishOn(Schedulers.newSingle("secondThread"))
				.log("reactor.2")
				.map( (d) -> {
					spanInOperation.set(SpanSubscriberTests.this.tracer.getCurrentSpan());
					return d + 1;
				})
				.map( d -> d + 1)
				.blockLast();

		Awaitility.await().untilAsserted(() -> {
			then(spanInOperation.get().getTraceId()).isEqualTo(span.getTraceId());
			then(ExceptionUtils.getLastException()).isNull();
		});
		then(this.tracer.getCurrentSpan()).isEqualTo(span);
		this.tracer.close(span);

		Span foo2 = this.tracer.createSpan("foo2");

		Flux.just(1, 2, 3)
				.publishOn(Schedulers.single())
				.log("reactor.")
				.map( d -> d + 1)
				.map( d -> d + 1)
				.map( (d) -> {
					spanInOperation.set(SpanSubscriberTests.this.tracer.getCurrentSpan());
					return d + 1;
				})
				.map( d -> d + 1)
				.blockLast();

		then(this.tracer.getCurrentSpan()).isEqualTo(foo2);
		then(ExceptionUtils.getLastException()).isNull();
		// parent cause there's an async span in the meantime
		then(spanInOperation.get().getTraceId()).isEqualTo(foo2.getTraceId());
		tracer.close(foo2);
	}

	@EnableAutoConfiguration
	@Configuration
	static class Config {
		@Bean Sampler sampler() {
			return new AlwaysSampler();
		}
	}
}