package org.springframework.cloud.brave.instrument.async;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.propagation.CurrentTraceContext;
import org.assertj.core.api.BDDAssertions;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.BDDMockito;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.cloud.brave.DefaultSpanNamer;
import org.springframework.cloud.brave.ErrorParser;
import org.springframework.cloud.brave.ExceptionMessageErrorParser;
import org.springframework.cloud.brave.SpanNamer;
import org.springframework.cloud.brave.util.ArrayListSpanReporter;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.BDDAssertions.then;

@RunWith(MockitoJUnitRunner.class)
public class TraceableExecutorServiceTests {
	private static int TOTAL_THREADS = 10;

	@Mock BeanFactory beanFactory;
	ExecutorService executorService = Executors.newFixedThreadPool(3);
	ExecutorService traceManagerableExecutorService;
	ArrayListSpanReporter reporter = new ArrayListSpanReporter();
	Tracing tracing = Tracing.newBuilder()
			.currentTraceContext(CurrentTraceContext.Default.create())
			.spanReporter(this.reporter)
			.build();
	SpanVerifyingRunnable spanVerifyingRunnable = new SpanVerifyingRunnable();

	@Before
	public void setup() {
		this.traceManagerableExecutorService = new TraceableExecutorService(beanFactory(), this.executorService);
		this.reporter.clear();
		this.spanVerifyingRunnable.clear();
	}

	@After
	public void tearDown() throws Exception {
		this.traceManagerableExecutorService.shutdown();
		this.executorService.shutdown();
		if (Tracing.current() != null) {
			Tracing.current().close();
		}
	}

	@Test
	public void should_propagate_trace_id_and_set_new_span_when_traceable_executor_service_is_executed()
			throws Exception {
		Span span = this.tracing.tracer().nextSpan().name("http:PARENT");
		try (Tracer.SpanInScope ws = this.tracing.tracer().withSpanInScope(span.start())) {
			CompletableFuture.allOf(runnablesExecutedViaTraceManagerableExecutorService()).get();
		} finally {
			span.finish();
		}

		then(this.spanVerifyingRunnable.traceIds.stream().distinct()
				.collect(toList())).containsOnly(span.context().traceId());
		then(this.spanVerifyingRunnable.spanIds.stream().distinct()
				.collect(toList())).hasSize(TOTAL_THREADS);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void should_wrap_methods_in_trace_representation_only_for_non_tracing_callables() throws Exception {
		ExecutorService executorService = Mockito.mock(ExecutorService.class);
		TraceableExecutorService traceExecutorService = new TraceableExecutorService(beanFactory(), executorService);

		traceExecutorService.invokeAll(callables());
		BDDMockito.then(executorService).should().invokeAll(BDDMockito.argThat(
				withSpanContinuingTraceCallablesOnly()));

		traceExecutorService.invokeAll(callables(), 1L, TimeUnit.DAYS);
		BDDMockito.then(executorService).should().invokeAll(BDDMockito.argThat(
				withSpanContinuingTraceCallablesOnly()),
				BDDMockito.eq(1L) , BDDMockito.eq(TimeUnit.DAYS));

		traceExecutorService.invokeAny(callables());
		BDDMockito.then(executorService).should().invokeAny(BDDMockito.argThat(
				withSpanContinuingTraceCallablesOnly()));

		traceExecutorService.invokeAny(callables(), 1L, TimeUnit.DAYS);
		BDDMockito.then(executorService).should().invokeAny(BDDMockito.argThat(
				withSpanContinuingTraceCallablesOnly()),
				BDDMockito.eq(1L) , BDDMockito.eq(TimeUnit.DAYS));
	}

	private ArgumentMatcher<Collection<? extends Callable<Object>>> withSpanContinuingTraceCallablesOnly() {
		return argument -> {
			try {
				BDDAssertions.then(argument)
						.flatExtracting(Object::getClass)
						.containsOnlyElementsOf(Collections.singletonList(TraceCallable.class));
			} catch (AssertionError e) {
				return false;
			}
			return true;
		};
	}

	private List callables() {
		List list = new ArrayList<>();
		list.add(new TraceCallable<>(this.tracing, new DefaultSpanNamer(),
				new ExceptionMessageErrorParser(), () -> "foo"));
		list.add((Callable) () -> "bar");
		return list;
	}

	@Test
	public void should_propagate_trace_info_when_compleable_future_is_used() throws Exception {
		ExecutorService executorService = this.executorService;
		BeanFactory beanFactory = beanFactory();
		// tag::completablefuture[]
		CompletableFuture<Long> completableFuture = CompletableFuture.supplyAsync(() -> {
			// perform some logic
			return 1_000_000L;
		}, new TraceableExecutorService(beanFactory, executorService,
				// 'calculateTax' explicitly names the span - this param is optional
				"calculateTax"));
		// end::completablefuture[]

		then(completableFuture.get()).isEqualTo(1_000_000L);
		then(this.tracing.tracer().currentSpan()).isNull();
	}

	private CompletableFuture<?>[] runnablesExecutedViaTraceManagerableExecutorService() {
		List<CompletableFuture<?>> futures = new ArrayList<>();
		for (int i = 0; i < TOTAL_THREADS; i++) {
			futures.add(CompletableFuture.runAsync(this.spanVerifyingRunnable, this.traceManagerableExecutorService));
		}
		return futures.toArray(new CompletableFuture[futures.size()]);
	}
	
	BeanFactory beanFactory() {
		BDDMockito.given(this.beanFactory.getBean(Tracing.class)).willReturn(this.tracing);
		BDDMockito.given(this.beanFactory.getBean(SpanNamer.class)).willReturn(new DefaultSpanNamer());
		BDDMockito.given(this.beanFactory.getBean(ErrorParser.class)).willReturn(new ExceptionMessageErrorParser());
		return this.beanFactory;
	}

	class SpanVerifyingRunnable implements Runnable {

		Queue<Long> traceIds = new ConcurrentLinkedQueue<>();
		Queue<Long> spanIds = new ConcurrentLinkedQueue<>();

		@Override
		public void run() {
			Span span = Tracing.currentTracer().currentSpan();
			this.traceIds.add(span.context().traceId());
			this.spanIds.add(span.context().spanId());
		}

		void clear() {
			this.traceIds.clear();
			this.spanIds.clear();
		}
	}

}
