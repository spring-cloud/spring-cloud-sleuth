package org.springframework.cloud.sleuth.instrument.rxjava2;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.sleuth.TraceKeys;

import brave.Span;
import brave.Tracer;
import io.reactivex.functions.Function;
import io.reactivex.plugins.RxJavaPlugins;
import io.reactivex.schedulers.SchedulerRunnableIntrospection;

/**
 * {@link RxJavaPlugins} setup schedule handler into tracing for all schedulers
 * representation.
 *
 * @author Łukasz Guz
 * @author Jakub Pyda
 */
class SleuthRxJava2SchedulersHandler {

	private static final Log log = LogFactory
			.getLog(SleuthRxJava2SchedulersHandler.class);

	private static final String RXJAVA_COMPONENT = "rxjava2";

	SleuthRxJava2SchedulersHandler(Tracer tracer, TraceKeys traceKeys,
			List<String> threadsToSample) {
		try {
			Function<? super Runnable, ? extends Runnable> delegate = RxJavaPlugins
					.getScheduleHandler();
			if (delegate instanceof SleuthRxJava2SchedulersHandler.ScheduleHandler) {
				return;
			}
			logCurrentStateOfRxJavaPlugins();
			RxJavaPlugins.reset();
			RxJavaPlugins.setScheduleHandler(
					new ScheduleHandler(tracer, traceKeys, threadsToSample, delegate));
		}
		catch (Exception e) {
			log.error("Failed to register Sleuth RxJava2 SchedulersHook", e);
		}
	}

	private void logCurrentStateOfRxJavaPlugins() {
		if (log.isDebugEnabled()) {
			log.debug("Registering Sleuth RxJava2 Schedulers Hook.");
		}
	}

	static class ScheduleHandler implements Function<Runnable, Runnable> {

		private final Tracer tracer;
		private final TraceKeys traceKeys;
		private final List<String> threadsToSample;
		private final Function<? super Runnable, ? extends Runnable> delegate;

		public ScheduleHandler(Tracer tracer, TraceKeys traceKeys,
				List<String> threadsToSample,
				Function<? super Runnable, ? extends Runnable> delegate) {
			this.tracer = tracer;
			this.traceKeys = traceKeys;
			this.threadsToSample = threadsToSample;
			this.delegate = delegate;
		}

		@Override
		public Runnable apply(Runnable action) throws Exception {
			if (isTraceActionDecoratedByRxWorker(action)) {
				return action;
			}
			Runnable wrappedAction = this.delegate != null ? this.delegate.apply(action)
					: action;
			return new SleuthRxJava2SchedulersHandler.TraceAction(this.tracer,
					this.traceKeys, wrappedAction, this.threadsToSample);
		}

		private boolean isTraceActionDecoratedByRxWorker(Runnable action) {
			if (action instanceof TraceAction) {
				return true;
			}
			else if (action instanceof SchedulerRunnableIntrospection) {
				SchedulerRunnableIntrospection runnableIntrospection = (SchedulerRunnableIntrospection) action;
				return runnableIntrospection.getWrappedRunnable() instanceof TraceAction;
			}
			return false;
		}
	}

	static class TraceAction implements Runnable {

		private final Runnable actual;
		private final Tracer tracer;
		private final TraceKeys traceKeys;
		private final Span parent;
		private final List<String> threadsToIgnore;

		public TraceAction(Tracer tracer, TraceKeys traceKeys, Runnable actual,
				List<String> threadsToIgnore) {
			this.tracer = tracer;
			this.traceKeys = traceKeys;
			this.threadsToIgnore = threadsToIgnore;
			this.parent = tracer.currentSpan();
			this.actual = actual;
		}

		@Override
		public void run() {
			String threadName = Thread.currentThread().getName();
			// don't create a span if the thread name is on a list of threads to ignore
			for (String threadToIgnore : this.threadsToIgnore) {
				if (threadName.matches(threadToIgnore)) {
					if (log.isTraceEnabled()) {
						log.trace(String.format(
								"Thread with name [%s] matches the regex [%s]. A span will not be created for this Thread.",
								threadName, threadToIgnore));
					}
					this.actual.run();
					return;
				}
			}

			Span span = this.parent;
			boolean created = false;
			if (span != null) {
				span = this.tracer.joinSpan(this.parent.context());
			}
			else {
				span = this.tracer.nextSpan().name(RXJAVA_COMPONENT).start();
				span.tag(
						this.traceKeys.getAsync().getPrefix()
								+ this.traceKeys.getAsync().getThreadNameKey(),
						Thread.currentThread().getName());
				created = true;
			}
			try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(span)) {
				this.actual.run();
			}
			finally {
				if (created) {
					span.finish();
				}
			}
		}
	}
}