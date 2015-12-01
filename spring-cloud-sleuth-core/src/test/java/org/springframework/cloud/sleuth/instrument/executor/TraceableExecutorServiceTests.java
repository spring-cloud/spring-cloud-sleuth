package org.springframework.cloud.sleuth.instrument.executor;

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
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Trace;
import org.springframework.cloud.sleuth.TraceManager;
import org.springframework.cloud.sleuth.event.SpanAcquiredEvent;
import org.springframework.cloud.sleuth.event.SpanReleasedEvent;
import org.springframework.cloud.sleuth.instrument.TraceRunnable;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.cloud.sleuth.trace.DefaultTraceManager;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.util.JdkIdGenerator;

public class TraceableExecutorServiceTests {
	private ApplicationEventPublisher publisher;
	private ExecutorService traceManagerableExecutorService;
	private TraceManager traceManager;
	private ExecutorService executorService;

	private int NUM_SPANS = 11;
	private int TOTAL_THREADS = 10;

	@Before
	public void setUp() throws Exception {
		this.publisher = Mockito.mock(ApplicationEventPublisher.class);
		this.traceManager = new DefaultTraceManager(new AlwaysSampler(), new JdkIdGenerator(), this.publisher);
		ExecutorService es = Executors.newFixedThreadPool(3);
		this.traceManagerableExecutorService = new TraceableExecutorService(es, this.traceManager);
		this.executorService = Executors.newFixedThreadPool(3);
	}

	@After
	public void tearDown() throws Exception {
		this.traceManager = null;
		this.traceManagerableExecutorService.shutdown();
		this.executorService.shutdown();
	}

	@Test
	public void test_whenTraceContextOfWorkerThreadIsNotClosed_thenException() {
		//THis test case ideally should fail but it is not failing because of the
		// https://github.com/spring-cloud/spring-cloud-sleuth/issues/60 comment two
		final AtomicInteger counter = new AtomicInteger(0);
		final CountDownLatch latch = new CountDownLatch(this.TOTAL_THREADS);
		Trace scope = this.traceManager.startSpan("PARENT");
		for (int i = 0; i < this.TOTAL_THREADS; i++) {
			this.traceManagerableExecutorService.execute(new MyRunnable(counter, latch));
		}

		try {
			latch.await();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		this.traceManager.close(scope);

		verify(this.publisher, times(this.NUM_SPANS)).publishEvent(isA(SpanAcquiredEvent.class));
		verify(this.publisher, times(this.NUM_SPANS)).publishEvent(isA(SpanReleasedEvent.class));

		ArgumentCaptor<ApplicationEvent> captor = ArgumentCaptor
				.forClass(ApplicationEvent.class);
		verify(this.publisher, atLeast(this.NUM_SPANS)).publishEvent(captor.capture());

		List<Span> spans = new ArrayList<>();
		for (ApplicationEvent event : captor.getAllValues()) {
			if (event instanceof SpanReleasedEvent) {
				spans.add(((SpanReleasedEvent) event).getSpan());
			}
		}

		assertThat("spans was wrong size", spans.size(), is(this.NUM_SPANS));
	}

	@Test
	public void test_whenTraceContextOfWorkerThreadIsClosed_thenNoException() {
		final AtomicInteger counter = new AtomicInteger(0);
		final CountDownLatch latch = new CountDownLatch(this.TOTAL_THREADS);
		Trace scope = this.traceManager.startSpan("PARENT");
		for (int i = 0; i < this.TOTAL_THREADS; i++) {
			final Runnable command = new TraceRunnable(this.traceManager, new MyRunnable(counter, latch));
			this.executorService.execute(command);
		}

		try {
			latch.await();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		this.traceManager.close(scope);

		verify(this.publisher, times(this.NUM_SPANS)).publishEvent(isA(SpanAcquiredEvent.class));
		verify(this.publisher, times(this.NUM_SPANS)).publishEvent(isA(SpanReleasedEvent.class));

		ArgumentCaptor<ApplicationEvent> captor = ArgumentCaptor
				.forClass(ApplicationEvent.class);
		verify(this.publisher, atLeast(this.NUM_SPANS)).publishEvent(captor.capture());

		List<Span> spans = new ArrayList<>();
		for (ApplicationEvent event : captor.getAllValues()) {
			if (event instanceof SpanReleasedEvent) {
				spans.add(((SpanReleasedEvent) event).getSpan());
			}
		}

		assertThat("spans was wrong size", spans.size(), is(this.NUM_SPANS));
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
				this.counter.incrementAndGet();
				this.latch.countDown();

			}
		}

	}

}
