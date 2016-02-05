package org.springframework.cloud.sleuth.instrument.async;

import lombok.SneakyThrows;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.instrument.async.TraceableExecutorService;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.cloud.sleuth.trace.DefaultTracer;
import org.springframework.cloud.sleuth.trace.TestSpanContextHolder;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.BDDAssertions.then;

@RunWith(MockitoJUnitRunner.class)
public class TraceableExecutorServiceTests {
	private static int TOTAL_THREADS = 10;

	@Mock ApplicationEventPublisher publisher;
	Tracer tracer;
	ExecutorService executorService = Executors.newFixedThreadPool(3);
	ExecutorService traceManagerableExecutorService;
	SpanVerifyingRunnable spanVerifyingRunnable = new SpanVerifyingRunnable();

	@Before
	public void setup() {
		this.tracer = new DefaultTracer(new AlwaysSampler(), new Random(), this.publisher);
		this.traceManagerableExecutorService = new TraceableExecutorService(this.executorService, this.tracer);
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
	@SneakyThrows
	public void should_propagate_trace_id_and_set_new_span_when_traceable_executor_service_is_executed() {
		Span span = this.tracer.startTrace("PARENT");
		CompletableFuture.allOf(runnablesExecutedViaTraceManagerableExecutorService()).get();
		this.tracer.close(span);

		then(this.spanVerifyingRunnable.traceIds.stream().distinct().collect(toList())).containsOnly(span.getTraceId());
		then(this.spanVerifyingRunnable.spanIds.stream().distinct().collect(toList())).hasSize(TOTAL_THREADS);
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
