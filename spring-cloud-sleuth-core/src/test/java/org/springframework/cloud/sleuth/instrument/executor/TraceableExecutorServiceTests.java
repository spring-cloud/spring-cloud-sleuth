package org.springframework.cloud.sleuth.instrument.executor;

import lombok.SneakyThrows;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Trace;
import org.springframework.cloud.sleuth.TraceManager;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.cloud.sleuth.trace.DefaultTraceManager;
import org.springframework.cloud.sleuth.trace.TraceContextHolder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.util.JdkIdGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
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
	TraceManager traceManager;
	ExecutorService executorService = Executors.newFixedThreadPool(3);
	ExecutorService traceManagerableExecutorService;
	SpanVerifyingRunnable spanVerifyingRunnable = new SpanVerifyingRunnable();

	@Before
	public void setup() {
		//traceManager = new DefaultTraceManager(new AlwaysSampler(), new RandomLongSpanIdGenerator(), publisher);
		traceManager = new DefaultTraceManager(new AlwaysSampler(), new JdkIdGenerator(), publisher);
		traceManagerableExecutorService = new TraceableExecutorService(executorService, traceManager);
		TraceContextHolder.removeCurrentTrace();
	}

	@After
	public void tearDown() throws Exception {
		traceManager = null;
		traceManagerableExecutorService.shutdown();
		executorService.shutdown();
		TraceContextHolder.removeCurrentTrace();
	}

	@Test
	@SneakyThrows
	public void should_propagate_trace_id_and_set_new_span_when_traceable_executor_service_is_executed() {
		Trace trace = traceManager.startSpan("PARENT");
		CompletableFuture.allOf(runnablesExecutedViaTraceManagerableExecutorService()).get();
		traceManager.close(trace);

		then(spanVerifyingRunnable.traceIds.stream().distinct().collect(toList())).containsOnly(trace.getSpan().getTraceId());
		then(spanVerifyingRunnable.spanIds.stream().distinct().collect(toList())).hasSize(TOTAL_THREADS);
	}

	private CompletableFuture[] runnablesExecutedViaTraceManagerableExecutorService() {
		List<CompletableFuture> futures = new ArrayList<>();
		for (int i = 0; i < TOTAL_THREADS; i++) {
			futures.add(CompletableFuture.runAsync(spanVerifyingRunnable, traceManagerableExecutorService));
		}
		return futures.toArray(new CompletableFuture[futures.size()]);
	}

	class SpanVerifyingRunnable implements Runnable {

		Queue<String> traceIds = new ConcurrentLinkedQueue<>();
		Queue<String> spanIds = new ConcurrentLinkedQueue<>();

		@Override
		public void run() {
			Span span = TraceContextHolder.getCurrentSpan();
			traceIds.add(span.getTraceId());
			spanIds.add(span.getSpanId());
		}

	}

}
