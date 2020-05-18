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

package org.springframework.cloud.sleuth.instrument.quartz;

import java.util.HashMap;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

import brave.Tracer.SpanInScope;
import brave.Tracing;
import brave.handler.MutableSpan;
import brave.propagation.Propagation.Setter;
import brave.propagation.StrictCurrentTraceContext;
import brave.test.IntegrationTestSpanHandler;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobKey;
import org.quartz.JobListener;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.Trigger.CompletedExecutionInstruction;
import org.quartz.TriggerKey;
import org.quartz.TriggerListener;
import org.quartz.impl.StdSchedulerFactory;
import org.quartz.listeners.JobListenerSupport;
import org.quartz.listeners.TriggerListenerSupport;
import org.quartz.utils.StringKeyDirtyFlagMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.quartz.JobBuilder.newJob;
import static org.quartz.TriggerBuilder.newTrigger;
import static org.springframework.cloud.sleuth.instrument.quartz.TracingJobListener.CONTEXT_SPAN_IN_SCOPE_KEY;
import static org.springframework.cloud.sleuth.instrument.quartz.TracingJobListener.CONTEXT_SPAN_KEY;
import static org.springframework.cloud.sleuth.instrument.quartz.TracingJobListener.TRIGGER_TAG_KEY;

/**
 * @author Branden Cash
 */
public class TracingJobListenerTest {

	@Rule
	public IntegrationTestSpanHandler spanHandler = new IntegrationTestSpanHandler();

	private static final JobKey SUCCESSFUL_JOB_KEY = new JobKey("SuccessfulJob");

	private static final JobKey EXCEPTIONAL_JOB_KEY = new JobKey("ExceptionalJob");

	private static final TriggerKey TRIGGER_KEY = new TriggerKey("ExampleTrigger");

	private TracingJobListener listener;

	private Scheduler scheduler;

	private CompletableFuture completableJob;

	private StrictCurrentTraceContext currentTraceContext = StrictCurrentTraceContext
			.create();

	private Tracing tracing = Tracing.newBuilder().addSpanHandler(spanHandler)
			.currentTraceContext(currentTraceContext).build();

	@Before
	public void setUp() throws Exception {
		listener = new TracingJobListener(tracing);
		completableJob = new CompleteableTriggerListener();

		scheduler = createScheduler(getClass().getSimpleName(), 1);
		scheduler.addJob(newJob(ExceptionalJob.class).withIdentity(EXCEPTIONAL_JOB_KEY)
				.storeDurably().build(), true);
		scheduler.addJob(newJob(SuccessfulJob.class).withIdentity(SUCCESSFUL_JOB_KEY)
				.storeDurably().build(), true);

		scheduler.getListenerManager().addTriggerListener(listener);
		scheduler.getListenerManager().addJobListener(listener);
		scheduler.getListenerManager()
				.addTriggerListener((CompleteableTriggerListener) completableJob);
		scheduler.getListenerManager()
				.addJobListener((CompleteableTriggerListener) completableJob);
		scheduler.start();
	}

	@After
	public void tearDown() throws Exception {
		this.scheduler.shutdown(true);
		this.tracing.close();
		this.currentTraceContext.close();
	}

	@Test
	public void should_return_class_name_all_the_time() {
		// when
		String name = listener.getName();

		// expect
		assertThat(name).isEqualTo(TracingJobListener.class.getName());
	}

	@Test
	public void should_complete_span_when_job_is_successful() throws Exception {
		// given
		Trigger trigger = newTrigger().forJob(SUCCESSFUL_JOB_KEY).startNow().build();

		// when
		runJob(trigger);

		// expect
		spanHandler.takeLocalSpan();
	}

	@Test
	public void should_have_span_with_proper_name_and_tag_when_job_is_successful()
			throws Exception {
		// given
		Trigger trigger = newTrigger().withIdentity(TRIGGER_KEY)
				.forJob(SUCCESSFUL_JOB_KEY).startNow().build();

		// when
		runJob(trigger);

		// expect
		MutableSpan span = spanHandler.takeLocalSpan();
		assertThat(span.name()).isEqualToIgnoringCase(SUCCESSFUL_JOB_KEY.toString());
		assertThat(span.tags().get(TRIGGER_TAG_KEY))
				.isEqualToIgnoringCase(TRIGGER_KEY.toString());
	}

	@Test
	public void should_complete_span_when_job_throws_exception() throws Exception {
		// given
		Trigger trigger = newTrigger().forJob(EXCEPTIONAL_JOB_KEY).startNow().build();

		// when
		runJob(trigger);

		// expect
		spanHandler.takeLocalSpan();
	}

	@Test
	public void should_complete_span_when_job_is_vetoed() throws Exception {
		// given
		scheduler.getListenerManager().addTriggerListener(new VetoJobTriggerListener());
		Trigger trigger = newTrigger().forJob(SUCCESSFUL_JOB_KEY).startNow().build();

		// when
		runJob(trigger);

		// expect
		spanHandler.takeLocalSpan();
	}

	@Test
	public void should_not_complete_span_when_context_is_modified_to_remove_keys()
			throws Exception {
		// given
		scheduler.getListenerManager().addJobListener(new ContextModifyingJobListener());
		Trigger trigger = newTrigger().forJob(EXCEPTIONAL_JOB_KEY).startNow().build();

		// when
		runJob(trigger);

		// expect no span
	}

	@Test
	public void should_have_parent_and_child_span_when_trigger_contains_span_info()
			throws Exception {
		// given
		JobDataMap data = new JobDataMap();
		addSpanToJobData(data);
		Trigger trigger = newTrigger().forJob(SUCCESSFUL_JOB_KEY).usingJobData(data)
				.startNow().build();

		// when
		runJob(trigger);

		// expect
		MutableSpan parent = spanHandler.takeLocalSpan();
		MutableSpan child = spanHandler.takeLocalSpan();
		assertThat(parent.parentId()).isNull();
		assertThat(child.parentId()).isEqualTo(parent.id());
	}

	@Test
	public void should_have_parent_and_child_span_when_trigger_job_data_was_created_with_differently_typed_map()
			throws Exception {
		// given
		JobDataMap data = new JobDataMap(new HashMap<Integer, Integer>());
		addSpanToJobData(data);
		Trigger trigger = newTrigger().forJob(SUCCESSFUL_JOB_KEY).usingJobData(data)
				.startNow().build();

		// when
		runJob(trigger);

		// expect
		MutableSpan parent = spanHandler.takeLocalSpan();
		MutableSpan child = spanHandler.takeLocalSpan();
		assertThat(parent.parentId()).isNull();
		assertThat(child.parentId()).isEqualTo(parent.id());
	}

	void runJob(Trigger trigger) throws SchedulerException {
		scheduler.scheduleJob(trigger);
		completableJob.join();
	}

	Scheduler createScheduler(String name, int threadPoolSize) throws SchedulerException {
		Properties config = new Properties();
		config.setProperty("org.quartz.scheduler.instanceName", name + "Scheduler");
		config.setProperty("org.quartz.scheduler.instanceId", "AUTO");
		config.setProperty("org.quartz.threadPool.threadCount",
				Integer.toString(threadPoolSize));
		config.setProperty("org.quartz.threadPool.class",
				"org.quartz.simpl.SimpleThreadPool");
		return new StdSchedulerFactory(config).getScheduler();
	}

	void addSpanToJobData(JobDataMap data) {
		brave.Span span = tracing.tracer().nextSpan().start();
		try (SpanInScope spanInScope = tracing.tracer().withSpanInScope(span)) {
			tracing.propagation()
					.injector((Setter<JobDataMap, String>) StringKeyDirtyFlagMap::put)
					.inject(tracing.currentTraceContext().get(), data);
		}
		finally {
			span.finish();
		}
	}

	public static class CompleteableTriggerListener extends CompletableFuture
			implements TriggerListener, JobListener {

		@Override
		public String getName() {
			return getClass().getName();
		}

		@Override
		public void triggerFired(Trigger trigger, JobExecutionContext context) {
		}

		@Override
		public boolean vetoJobExecution(Trigger trigger, JobExecutionContext context) {
			return false;
		}

		@Override
		public void triggerMisfired(Trigger trigger) {
		}

		@Override
		public void triggerComplete(Trigger trigger, JobExecutionContext context,
				CompletedExecutionInstruction triggerInstructionCode) {
			complete(context.getResult());
		}

		@Override
		public void jobToBeExecuted(JobExecutionContext context) {
		}

		@Override
		public void jobExecutionVetoed(JobExecutionContext context) {
			complete(context.getResult());
		}

		@Override
		public void jobWasExecuted(JobExecutionContext context,
				JobExecutionException jobException) {
		}

	}

	public static class VetoJobTriggerListener extends TriggerListenerSupport {

		@Override
		public String getName() {
			return getClass().getName();
		}

		@Override
		public boolean vetoJobExecution(Trigger trigger, JobExecutionContext context) {
			return true;
		}

	}

	public static class ContextModifyingJobListener extends JobListenerSupport {

		@Override
		public String getName() {
			return getClass().getName();
		}

		@Override
		public void jobToBeExecuted(JobExecutionContext context) {
			context.put(CONTEXT_SPAN_KEY, null);
			context.put(CONTEXT_SPAN_IN_SCOPE_KEY, null);
		}

	}

	public static class ExceptionalJob implements Job {

		@Override
		public void execute(JobExecutionContext context) throws JobExecutionException {
			throw new RuntimeException("Intentional Exception");
		}

	}

	public static class SuccessfulJob implements Job {

		@Override
		public void execute(JobExecutionContext context) throws JobExecutionException {
		}

	}

}
