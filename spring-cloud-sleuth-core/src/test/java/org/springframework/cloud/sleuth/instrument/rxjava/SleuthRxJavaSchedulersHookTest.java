package org.springframework.cloud.sleuth.instrument.rxjava;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.cloud.sleuth.TraceKeys;
import org.springframework.cloud.sleuth.Tracer;

import rx.functions.Action0;
import rx.plugins.RxJavaErrorHandler;
import rx.plugins.RxJavaObservableExecutionHook;
import rx.plugins.RxJavaSchedulersHook;
import rx.plugins.SleuthRxJavaPlugins;

import static org.assertj.core.api.BDDAssertions.then;

/**
 *
 * @author Shivang Shah
 */
@RunWith(MockitoJUnitRunner.class)
public class SleuthRxJavaSchedulersHookTest {

	@Mock
	Tracer tracer;
	TraceKeys traceKeys = new TraceKeys();

	private static StringBuilder caller;

	@Before
	@After
	public void setup() {
		SleuthRxJavaPlugins.resetPlugins();
		caller = new StringBuilder();
	}

	@Test
	public void should_not_override_existing_custom_hooks() {
		SleuthRxJavaPlugins.getInstance().registerErrorHandler(new MyRxJavaErrorHandler());
		SleuthRxJavaPlugins.getInstance().registerObservableExecutionHook(new MyRxJavaObservableExecutionHook());
		new SleuthRxJavaSchedulersHook(this.tracer, this.traceKeys);
		then(SleuthRxJavaPlugins.getInstance().getErrorHandler()).isExactlyInstanceOf(MyRxJavaErrorHandler.class);
		then(SleuthRxJavaPlugins.getInstance().getObservableExecutionHook()).isExactlyInstanceOf(MyRxJavaObservableExecutionHook.class);
	}

	@Test
	public void should_wrap_delegates_action_in_wrapped_action_when_delegate_is_present_on_schedule() {
		SleuthRxJavaPlugins.getInstance().registerSchedulersHook(new MyRxJavaSchedulersHook());
		SleuthRxJavaSchedulersHook schedulersHook = new SleuthRxJavaSchedulersHook(
				this.tracer, this.traceKeys);
		Action0 action = schedulersHook.onSchedule(() -> {
			caller = new StringBuilder("hello");
		});
		action.call();
		then(action).isInstanceOf(SleuthRxJavaSchedulersHook.TraceAction.class);
		then(caller.toString()).isEqualTo("called_from_schedulers_hook");
	}

	static class MyRxJavaObservableExecutionHook extends RxJavaObservableExecutionHook {
	}

	static class MyRxJavaSchedulersHook extends RxJavaSchedulersHook {

		@Override
		public Action0 onSchedule(Action0 action) {
			return () -> {
				caller = new StringBuilder("called_from_schedulers_hook");
			};
		}
	}

	static class MyRxJavaErrorHandler extends RxJavaErrorHandler {
	}
}
