/*
 * Copyright 2013-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.sleuth.instrument.async;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.Callable;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;

import brave.Tracing;
import brave.propagation.StrictCurrentTraceContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.BDDMockito;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.cloud.sleuth.DefaultSpanNamer;
import org.springframework.cloud.sleuth.SpanNamer;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.util.ErrorHandler;

@RunWith(MockitoJUnitRunner.class)
public class LazyTraceThreadPoolTaskSchedulerTests {

	StrictCurrentTraceContext currentTraceContext = StrictCurrentTraceContext.create();

	Tracing tracing = Tracing.newBuilder().currentTraceContext(currentTraceContext)
			.build();

	@Mock
	BeanFactory beanFactory;

	@Mock
	ThreadPoolTaskScheduler delegate;

	LazyTraceThreadPoolTaskScheduler executor;

	@Before
	public void setup() {
		this.executor = new LazyTraceThreadPoolTaskScheduler(beanFactory(),
				this.delegate);
	}

	@After
	public void close() {
		this.executor.shutdown();
		this.tracing.close();
		this.currentTraceContext.close();
	}

	BeanFactory beanFactory() {
		BDDMockito.given(this.beanFactory.getBean(Tracing.class))
				.willReturn(this.tracing);
		BDDMockito.given(this.beanFactory.getBean(SpanNamer.class))
				.willReturn(new DefaultSpanNamer());
		ContextRefreshedListenerAccessor.set(this.beanFactory, true);
		return this.beanFactory;
	}

	@Test
	public void setPoolSize() {
		this.executor.setPoolSize(10);

		BDDMockito.then(this.delegate).should().setPoolSize(10);
	}

	@Test
	public void setRemoveOnCancelPolicy() {
		this.executor.setRemoveOnCancelPolicy(true);

		BDDMockito.then(this.delegate).should().setRemoveOnCancelPolicy(true);
	}

	@Test
	public void setErrorHandler() {
		ErrorHandler handler = (throwable) -> {
		};
		this.executor.setErrorHandler(handler);

		BDDMockito.then(this.delegate).should().setErrorHandler(handler);
	}

	@Test
	public void getScheduledExecutor() {
		this.executor.getScheduledExecutor();

		BDDMockito.then(this.delegate).should().getScheduledExecutor();
	}

	@Test
	public void getPoolSize() {
		this.executor.getPoolSize();

		BDDMockito.then(this.delegate).should().getPoolSize();
	}

	@Test
	public void isRemoveOnCancelPolicy() {
		this.executor.setRemoveOnCancelPolicy(true);

		BDDMockito.then(this.delegate).should().setRemoveOnCancelPolicy(true);
	}

	@Test
	public void getActiveCount() {
		this.executor.getActiveCount();

		BDDMockito.then(this.delegate).should().getActiveCount();
	}

	@Test
	public void execute() {
		Runnable r = () -> {
		};
		this.executor.execute(r);

		BDDMockito.then(this.delegate).should().execute(r);
	}

	@Test
	public void execute1() {
		Runnable r = () -> {
		};
		this.executor.execute(r, 10L);

		BDDMockito.then(this.delegate).should()
				.execute(BDDMockito.any(TraceRunnable.class), BDDMockito.eq(10L));
	}

	@Test
	public void submit() {
		Runnable c = () -> {
		};
		this.executor.submit(c);

		BDDMockito.then(this.delegate).should()
				.submit(BDDMockito.any(TraceRunnable.class));
	}

	@Test
	public void submit1() {
		Callable c = () -> null;
		this.executor.submit(c);

		BDDMockito.then(this.delegate).should()
				.submit(BDDMockito.any(TraceCallable.class));
	}

	@Test
	public void submitListenable() {
		Runnable c = () -> {
		};
		this.executor.submitListenable(c);

		BDDMockito.then(this.delegate).should()
				.submitListenable(BDDMockito.any(TraceRunnable.class));
	}

	@Test
	public void submitListenable1() {
		Callable c = () -> null;
		this.executor.submitListenable(c);

		BDDMockito.then(this.delegate).should()
				.submitListenable(BDDMockito.any(TraceCallable.class));
	}

	@Test
	public void prefersShortLivedTasks() {
		this.executor.prefersShortLivedTasks();

		BDDMockito.then(this.delegate).should().prefersShortLivedTasks();
	}

	@Test
	public void schedule() {
		Runnable c = () -> {
		};
		Trigger trigger = triggerContext -> null;
		this.executor.schedule(c, trigger);

		BDDMockito.then(this.delegate).should()
				.schedule(BDDMockito.any(TraceRunnable.class), BDDMockito.eq(trigger));
	}

	@Test
	public void schedule1() {
		Runnable c = () -> {
		};
		Date date = new Date();
		this.executor.schedule(c, date);

		BDDMockito.then(this.delegate).should()
				.schedule(BDDMockito.any(TraceRunnable.class), BDDMockito.eq(date));
	}

	@Test
	public void scheduleAtFixedRate() {
		Runnable c = () -> {
		};
		Date date = new Date();
		this.executor.scheduleAtFixedRate(c, date, 10L);

		BDDMockito.then(this.delegate).should().scheduleAtFixedRate(
				BDDMockito.any(TraceRunnable.class), BDDMockito.eq(date),
				BDDMockito.eq(10L));
	}

	@Test
	public void scheduleAtFixedRate1() {
		Runnable c = () -> {
		};
		this.executor.scheduleAtFixedRate(c, 10L);

		BDDMockito.then(this.delegate).should().scheduleAtFixedRate(
				BDDMockito.any(TraceRunnable.class), BDDMockito.eq(10L));
	}

	@Test
	public void scheduleWithFixedDelay() {
		Runnable c = () -> {
		};
		Date date = new Date();
		this.executor.scheduleWithFixedDelay(c, date, 10L);

		BDDMockito.then(this.delegate).should().scheduleWithFixedDelay(
				BDDMockito.any(TraceRunnable.class), BDDMockito.eq(date),
				BDDMockito.eq(10L));
	}

	@Test
	public void scheduleWithFixedDelay1() {
		Runnable c = () -> {
		};
		this.executor.scheduleWithFixedDelay(c, 10L);

		BDDMockito.then(this.delegate).should().scheduleWithFixedDelay(
				BDDMockito.any(TraceRunnable.class), BDDMockito.eq(10L));
	}

	@Test
	public void scheduleWithFixedDelay2() {
		Runnable c = () -> {
		};
		Instant instant = Instant.now();
		Duration duration = Duration.ZERO;
		this.executor.scheduleWithFixedDelay(c, instant, duration);

		BDDMockito.then(this.delegate).should().scheduleWithFixedDelay(
				BDDMockito.any(TraceRunnable.class), BDDMockito.eq(instant),
				BDDMockito.eq(duration));
	}

	@Test
	public void scheduleWithFixedDelay3() {
		Runnable c = () -> {
		};
		Duration duration = Duration.ZERO;
		this.executor.scheduleWithFixedDelay(c, duration);

		BDDMockito.then(this.delegate).should().scheduleWithFixedDelay(
				BDDMockito.any(TraceRunnable.class), BDDMockito.eq(duration));
	}

	@Test
	public void setThreadFactory() {
		ThreadFactory threadFactory = r -> null;
		this.executor.setThreadFactory(threadFactory);

		BDDMockito.then(this.delegate).should().setThreadFactory(threadFactory);
	}

	@Test
	public void setThreadNamePrefix() {
		this.executor.setThreadNamePrefix("foo");

		BDDMockito.then(this.delegate).should().setThreadNamePrefix("foo");
	}

	@Test
	public void setRejectedExecutionHandler() {
		RejectedExecutionHandler handler = (r, executor1) -> {
		};
		this.executor.setRejectedExecutionHandler(handler);

		BDDMockito.then(this.delegate).should().setRejectedExecutionHandler(handler);
	}

	@Test
	public void setWaitForTasksToCompleteOnShutdown() {
		this.executor.setWaitForTasksToCompleteOnShutdown(true);

		BDDMockito.then(this.delegate).should().setWaitForTasksToCompleteOnShutdown(true);
	}

	@Test
	public void setAwaitTerminationSeconds() {
		this.executor.setAwaitTerminationSeconds(10);

		BDDMockito.then(this.delegate).should().setAwaitTerminationSeconds(10);
	}

	@Test
	public void setBeanName() {
		this.executor.setBeanName("foo");

		BDDMockito.then(this.delegate).should().setBeanName("foo");
	}

	@Test
	public void afterPropertiesSet() {
		this.executor.afterPropertiesSet();

		BDDMockito.then(this.delegate).should().afterPropertiesSet();
	}

	@Test
	public void initialize() {
		this.executor.initialize();

		BDDMockito.then(this.delegate).should().initialize();
	}

	@Test
	public void destroy() {
		this.executor.destroy();

		BDDMockito.then(this.delegate).should().destroy();
	}

	@Test
	public void shutdown() {
		this.executor.shutdown();

		BDDMockito.then(this.delegate).should().shutdown();
	}

	@Test
	public void newThread() {
		Runnable runnable = () -> {
		};
		this.executor.newThread(runnable);

		BDDMockito.then(this.delegate).should().newThread(runnable);
	}

	@Test
	public void getThreadNamePrefix() {
		this.executor.getThreadNamePrefix();

		BDDMockito.then(this.delegate).should().getThreadNamePrefix();
	}

	@Test
	public void setThreadPriority() {
		this.executor.setThreadPriority(10);

		BDDMockito.then(this.delegate).should().setThreadPriority(10);
	}

	@Test
	public void getThreadPriority() {
		this.executor.getThreadPriority();

		BDDMockito.then(this.delegate).should().getThreadPriority();
	}

	@Test
	public void setDaemon() {
		this.executor.setDaemon(true);

		BDDMockito.then(this.delegate).should().setDaemon(true);
	}

	@Test
	public void isDaemon() {
		this.executor.isDaemon();

		BDDMockito.then(this.delegate).should().isDaemon();
	}

	@Test
	public void setThreadGroupName() {
		this.executor.setThreadGroupName("foo");

		BDDMockito.then(this.delegate).should().setThreadGroupName("foo");
	}

	@Test
	public void setThreadGroup() {
		ThreadGroup threadGroup = new ThreadGroup("foo");
		this.executor.setThreadGroup(threadGroup);

		BDDMockito.then(this.delegate).should().setThreadGroup(threadGroup);
	}

	@Test
	public void getThreadGroup() {
		this.executor.getThreadGroup();

		BDDMockito.then(this.delegate).should().getThreadGroup();
	}

	@Test
	public void createThread() {
		Runnable r = () -> {
		};
		this.executor.createThread(r);

		BDDMockito.then(this.delegate).should().createThread(r);
	}

	@Test
	public void schedule2() {
		Runnable r = () -> {
		};
		Instant instant = Instant.now();
		this.executor.schedule(r, instant);

		BDDMockito.then(this.delegate).should()
				.schedule(BDDMockito.any(TraceRunnable.class), BDDMockito.eq(instant));
	}

	@Test
	public void scheduleAtFixedRate2() {
		Runnable r = () -> {
		};
		Instant instant = Instant.now();
		Duration duration = Duration.ZERO;
		this.executor.scheduleAtFixedRate(r, instant, duration);

		BDDMockito.then(this.delegate).should().scheduleAtFixedRate(
				BDDMockito.any(TraceRunnable.class), BDDMockito.eq(instant),
				BDDMockito.eq(duration));
	}

	@Test
	public void scheduleAtFixedRate3() {
		Runnable r = () -> {
		};
		Duration duration = Duration.ZERO;
		this.executor.scheduleAtFixedRate(r, duration);

		BDDMockito.then(this.delegate).should().scheduleAtFixedRate(
				BDDMockito.any(TraceRunnable.class), BDDMockito.eq(duration));
	}

}
