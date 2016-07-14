package org.springframework.cloud.sleuth.instrument.async;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.BDDMockito;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.cloud.sleuth.DefaultSpanNamer;
import org.springframework.cloud.sleuth.NoOpSpanReporter;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanNamer;
import org.springframework.cloud.sleuth.TraceKeys;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.assertions.SleuthAssertions;
import org.springframework.cloud.sleuth.log.NoOpSpanLogger;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.cloud.sleuth.trace.DefaultTracer;
import org.springframework.cloud.sleuth.trace.TestSpanContextHolder;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.BDDAssertions.then;

@RunWith(MockitoJUnitRunner.class)
public class TraceableExecutorServiceTests {
	private static int TOTAL_THREADS = 10;

	@Mock SpanNamer spanNamer;
	Tracer tracer;
	ExecutorService executorService = Executors.newFixedThreadPool(3);
	ExecutorService traceManagerableExecutorService;
	SpanVerifyingRunnable spanVerifyingRunnable = new SpanVerifyingRunnable();

	@Before
	public void setup() {
		this.tracer = new DefaultTracer(new AlwaysSampler(), new Random(),
				this.spanNamer, new NoOpSpanLogger(), new NoOpSpanReporter());
		this.traceManagerableExecutorService = new TraceableExecutorService(this.executorService,
				this.tracer, new TraceKeys(), this.spanNamer);
		TestSpanContextHolder.removeCurrentSpan();
	}

	@After
	public void tearDown() throws Exception {
		this.tracer = null;
		this.traceManagerableExecutorService.shutdown();
		this.executorService.shutdown();
		TestSpanContextHolder.removeCurrentSpan();
	}

	@Test
	public void should_propagate_trace_id_and_set_new_span_when_traceable_executor_service_is_executed()
			throws Exception {
		Span span = this.tracer.createSpan("http:PARENT");
		CompletableFuture.allOf(runnablesExecutedViaTraceManagerableExecutorService()).get();
		this.tracer.close(span);

		then(this.spanVerifyingRunnable.traceIds.stream().distinct().collect(toList())).containsOnly(span.getTraceId());
		then(this.spanVerifyingRunnable.spanIds.stream().distinct().collect(toList())).hasSize(TOTAL_THREADS);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void should_wrap_methods_in_trace_representation_only_for_non_tracing_callables() throws Exception {
		ExecutorService executorService = Mockito.mock(ExecutorService.class);
		TraceableExecutorService traceManagerableExecutorService = new TraceableExecutorService(
				executorService, this.tracer, new TraceKeys(), this.spanNamer);

		traceManagerableExecutorService.invokeAll(callables());
		BDDMockito.then(executorService).should().invokeAll(BDDMockito.argThat(withOneLocalComponentTraceCallable()));

		traceManagerableExecutorService.invokeAll(callables(), 1L, TimeUnit.DAYS);
		BDDMockito.then(executorService).should().invokeAll(BDDMockito.argThat(withOneLocalComponentTraceCallable()),
				BDDMockito.eq(1L) , BDDMockito.eq(TimeUnit.DAYS));

		traceManagerableExecutorService.invokeAny(callables());
		BDDMockito.then(executorService).should().invokeAny(BDDMockito.argThat(withOneLocalComponentTraceCallable()));

		traceManagerableExecutorService.invokeAny(callables(), 1L, TimeUnit.DAYS);
		BDDMockito.then(executorService).should().invokeAny(BDDMockito.argThat(withOneLocalComponentTraceCallable()),
				BDDMockito.eq(1L) , BDDMockito.eq(TimeUnit.DAYS));
	}

	private Matcher<Collection<? extends Callable<Object>>> withOneLocalComponentTraceCallable() {
		return new TypeSafeMatcher<Collection<? extends Callable<Object>>>() {
			@Override
			protected boolean matchesSafely(Collection<? extends Callable<Object>> item) {
				try {
					SleuthAssertions.then(item)
							.flatExtracting(Object::getClass)
							.containsExactly(LocalComponentTraceCallable.class);
				} catch (AssertionError e) {
					return false;
				}
				return true;
			}

			@Override public void describeTo(Description description) {
				description.appendText("should contain a single local component trace callable");
			}
		};
	}

	private List callables() {
		List list = new ArrayList<>();
		list.add(new LocalComponentTraceCallable<Object>(this.tracer, new TraceKeys(), this.spanNamer, () -> "foo"));
		list.add((Callable) () -> "bar");
		return list;
	}

	@Test
	public void should_propagate_trace_info_when_compleable_future_is_used() throws Exception {
		Tracer tracer = this.tracer;
		TraceKeys traceKeys = new TraceKeys();
		SpanNamer spanNamer = new DefaultSpanNamer();
		ExecutorService executorService = this.executorService;

		// tag::completablefuture[]
		CompletableFuture<Long> completableFuture = CompletableFuture.supplyAsync(() -> {
			// perform some logic
			return 1_000_000L;
		}, new TraceableExecutorService(executorService,
				// 'calculateTax' explicitly names the span - this param is optional
				tracer, traceKeys, spanNamer, "calculateTax"));
		// end::completablefuture[]

		then(completableFuture.get()).isEqualTo(1_000_000L);
		then(this.tracer.getCurrentSpan()).isNull();
	}

	private CompletableFuture<?>[] runnablesExecutedViaTraceManagerableExecutorService() {
		List<CompletableFuture<?>> futures = new ArrayList<>();
		for (int i = 0; i < TOTAL_THREADS; i++) {
			futures.add(CompletableFuture.runAsync(this.spanVerifyingRunnable, this.traceManagerableExecutorService));
		}
		return futures.toArray(new CompletableFuture[futures.size()]);
	}

	class SpanVerifyingRunnable implements Runnable {

		Queue<Long> traceIds = new ConcurrentLinkedQueue<>();
		Queue<Long> spanIds = new ConcurrentLinkedQueue<>();

		@Override
		public void run() {
			Span span = TestSpanContextHolder.getCurrentSpan();
			this.traceIds.add(span.getTraceId());
			this.spanIds.add(span.getSpanId());
		}

	}

}
