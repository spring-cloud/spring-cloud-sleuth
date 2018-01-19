package org.springframework.cloud.sleuth.instrument.rxjava;

import java.util.List;

import brave.Span;
import brave.Tracer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.sleuth.TraceKeys;
import rx.functions.Action0;
import rx.plugins.RxJavaErrorHandler;
import rx.plugins.RxJavaObservableExecutionHook;
import rx.plugins.RxJavaPlugins;
import rx.plugins.RxJavaSchedulersHook;

/**
 * {@link RxJavaSchedulersHook} that wraps an {@link Action0} into its tracing
 * representation.
 *
 * @author Shivang Shah
 * @since 1.0.0
 */
class SleuthRxJavaSchedulersHook extends RxJavaSchedulersHook {

	private static final Log log = LogFactory.getLog(
			SleuthRxJavaSchedulersHook.class);

	private static final String RXJAVA_COMPONENT = "rxjava";
	private final Tracer tracer;
	private final TraceKeys traceKeys;
	private final List<String> threadsToSample;
	private RxJavaSchedulersHook delegate;

	SleuthRxJavaSchedulersHook(Tracer tracer, TraceKeys traceKeys,
			List<String> threadsToSample) {
		this.tracer = tracer;
		this.traceKeys = traceKeys;
		this.threadsToSample = threadsToSample;
		try {
			this.delegate = RxJavaPlugins.getInstance().getSchedulersHook();
			if (this.delegate instanceof SleuthRxJavaSchedulersHook) {
				return;
			}
			RxJavaErrorHandler errorHandler = RxJavaPlugins.getInstance().getErrorHandler();
			RxJavaObservableExecutionHook observableExecutionHook
				= RxJavaPlugins.getInstance().getObservableExecutionHook();
			logCurrentStateOfRxJavaPlugins(errorHandler, observableExecutionHook);
			RxJavaPlugins.getInstance().reset();
			RxJavaPlugins.getInstance().registerSchedulersHook(this);
			RxJavaPlugins.getInstance().registerErrorHandler(errorHandler);
			RxJavaPlugins.getInstance().registerObservableExecutionHook(observableExecutionHook);
		} catch (Exception e) {
			log.error("Failed to register Sleuth RxJava SchedulersHook", e);
		}
	}

	private void logCurrentStateOfRxJavaPlugins(RxJavaErrorHandler errorHandler,
		RxJavaObservableExecutionHook observableExecutionHook) {
		if (log.isDebugEnabled()) {
			log.debug("Current RxJava plugins configuration is ["
					+ "schedulersHook [" + this.delegate + "],"
					+ "errorHandler [" + errorHandler + "],"
					+ "observableExecutionHook [" + observableExecutionHook + "],"
					+ "]");
			log.debug("Registering Sleuth RxJava Schedulers Hook.");
		}
	}

	@Override
	public Action0 onSchedule(Action0 action) {
		if (action instanceof TraceAction) {
			return action;
		}
		Action0 wrappedAction = this.delegate != null
			? this.delegate.onSchedule(action) : action;
		if (wrappedAction instanceof TraceAction) {
			return action;
		}
		return super.onSchedule(new TraceAction(this.tracer, this.traceKeys, wrappedAction,
				this.threadsToSample));
	}

	static class TraceAction implements Action0 {

		private final Action0 actual;
		private final Tracer tracer;
		private final TraceKeys traceKeys;
		private final Span parent;
		private final List<String> threadsToIgnore;

		public TraceAction(Tracer tracer, TraceKeys traceKeys, Action0 actual,
				List<String> threadsToIgnore) {
			this.tracer = tracer;
			this.traceKeys = traceKeys;
			this.threadsToIgnore = threadsToIgnore;
			this.parent = this.tracer.currentSpan();
			this.actual = actual;
		}

		@SuppressWarnings("Duplicates")
		@Override
		public void call() {
			// don't create a span if the thread name is on a list of threads to ignore
			for (String threadToIgnore : this.threadsToIgnore) {
				String threadName = Thread.currentThread().getName();
				if (threadName.matches(threadToIgnore)) {
					if (log.isTraceEnabled()) {
						log.trace(String.format(
								"Thread with name [%s] matches the regex [%s]. A span will not be created for this Thread.",
								threadName, threadToIgnore));
					}
					this.actual.call();
					return;
				}
			}
			Span span = this.parent;
			boolean created = false;
			if (span != null) {
				span = this.tracer.joinSpan(this.parent.context());
			} else {
				span = this.tracer.nextSpan().name(RXJAVA_COMPONENT).start();
				span.tag(this.traceKeys.getAsync().getPrefix()
					+ this.traceKeys.getAsync().getThreadNameKey(),
						Thread.currentThread().getName());
				created = true;
			}
			try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(span)) {
				this.actual.call();
			} finally {
				if (created) {
					span.finish();
				}
			}
		}
	}
}
