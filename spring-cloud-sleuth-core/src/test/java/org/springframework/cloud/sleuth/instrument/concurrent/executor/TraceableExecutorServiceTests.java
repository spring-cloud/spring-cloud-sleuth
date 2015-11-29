package org.springframework.cloud.sleuth.instrument.concurrent.executor;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.cloud.sleuth.RandomUuidGenerator;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Trace;
import org.springframework.cloud.sleuth.TraceContextHolder;
import org.springframework.cloud.sleuth.TraceScope;
import org.springframework.cloud.sleuth.event.SpanAcquiredEvent;
import org.springframework.cloud.sleuth.event.SpanReleasedEvent;
import org.springframework.cloud.sleuth.instrument.TraceRunnable;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.cloud.sleuth.trace.DefaultTrace;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;

public class TraceableExecutorServiceTests {
	private ApplicationEventPublisher publisher;
	private ExecutorService traceableExecutorService;
	private Trace trace;
	private ExecutorService executorService;
	
	private int NUM_SPANS = 11;
	private int TOTAL_THREADS = 10;

	@Before
	public void setUp() throws Exception {
		this.publisher = Mockito.mock(ApplicationEventPublisher.class);
		this.trace = new DefaultTrace(new AlwaysSampler(), new RandomUuidGenerator(), this.publisher);
		ExecutorService es = Executors.newFixedThreadPool(3);
		this.traceableExecutorService = new TraceableExecutorService(es, this.trace);
		this.executorService = Executors.newFixedThreadPool(3);
	}

	@After
	public void tearDown() throws Exception {
		this.trace = null;
		this.traceableExecutorService.shutdown();
		this.executorService.shutdown();
	}
	
	@Test
	public void test_whenTraceContextOfWorkerThreadIsNotClosed_thenException() {
		//THis test case ideally should fail but it is not failing because of the
		// https://github.com/spring-cloud/spring-cloud-sleuth/issues/60 comment two
		final AtomicInteger counter = new AtomicInteger(0);
		final CountDownLatch latch = new CountDownLatch(TOTAL_THREADS);
		TraceScope scope = this.trace.startSpan("PARENT");
		for (int i = 0; i < TOTAL_THREADS; i++) {
			traceableExecutorService.execute(new MyRunnable(counter, latch));
		}

		try {
			latch.await();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		scope.close();

		verify(this.publisher, times(NUM_SPANS)).publishEvent(isA(SpanAcquiredEvent.class));
		verify(publisher, times(NUM_SPANS)).publishEvent(isA(SpanReleasedEvent.class));

		ArgumentCaptor<ApplicationEvent> captor = ArgumentCaptor
				.forClass(ApplicationEvent.class);
		verify(publisher, atLeast(NUM_SPANS)).publishEvent(captor.capture());

		List<Span> spans = new ArrayList<>();
		for (ApplicationEvent event : captor.getAllValues()) {
			if (event instanceof SpanReleasedEvent) {
				spans.add(((SpanReleasedEvent) event).getSpan());
			}
		}

		assertThat("spans was wrong size", spans.size(), is(NUM_SPANS));
	}
	
	@Test
	public void test_whenTraceContextOfWorkerThreadIsClosed_thenNoException() {
		final AtomicInteger counter = new AtomicInteger(0);
		final CountDownLatch latch = new CountDownLatch(TOTAL_THREADS);
		TraceScope scope = this.trace.startSpan("PARENT");
		for (int i = 0; i < TOTAL_THREADS; i++) {
			final TraceRunnableAdapter command = new TraceRunnableAdapter(new TraceRunnable(this.trace, new MyRunnable(counter, latch)));
			executorService.execute(command);
		}

		try {
			latch.await();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		scope.close();

		verify(this.publisher, times(NUM_SPANS)).publishEvent(isA(SpanAcquiredEvent.class));
		verify(publisher, times(NUM_SPANS)).publishEvent(isA(SpanReleasedEvent.class));

		ArgumentCaptor<ApplicationEvent> captor = ArgumentCaptor
				.forClass(ApplicationEvent.class);
		verify(publisher, atLeast(NUM_SPANS)).publishEvent(captor.capture());

		List<Span> spans = new ArrayList<>();
		for (ApplicationEvent event : captor.getAllValues()) {
			if (event instanceof SpanReleasedEvent) {
				spans.add(((SpanReleasedEvent) event).getSpan());
			}
		}

		assertThat("spans was wrong size", spans.size(), is(NUM_SPANS));
	}
	
	class TraceRunnableAdapter implements Runnable {
		private final Runnable delegate;
		public TraceRunnableAdapter(final Runnable delegate) {
			this.delegate = delegate;
		}
		
		@Override
		public void run() {
			try {
				this.delegate.run();
			}
			finally {
				TraceContextHolder.removeCurrentSpan();
			}
		}
		
	}

	class MyRunnable implements Runnable {
		private final AtomicInteger counter;
		private final CountDownLatch latch;
		
		MyRunnable(final AtomicInteger counter, final CountDownLatch latch) {
			this.counter = counter;
			this.latch = latch;
		}
		
		@Override
		public void run() {
			try {
				try {
					TimeUnit.MILLISECONDS.sleep(100l);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			finally {
				counter.incrementAndGet();
				latch.countDown();
				
			}
		}
		
	}

}
