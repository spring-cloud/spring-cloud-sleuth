/*
 * Copyright 2013-2020 the original author or authors.
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

import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobListener;
import org.quartz.Trigger;
import org.quartz.Trigger.CompletedExecutionInstruction;
import org.quartz.TriggerListener;

import org.springframework.cloud.sleuth.api.Span;
import org.springframework.cloud.sleuth.api.Tracer;
import org.springframework.cloud.sleuth.api.propagation.Propagator;

/**
 * {@link org.quartz.JobListener JobListener} that will wrap a span around quartz jobs
 * when they start and finish.
 *
 * @author Branden Cash
 * @since 2.2.0
 */
class TracingJobListener implements JobListener, TriggerListener {

	static final String TRIGGER_TAG_KEY = "quartz.trigger";

	static final String CONTEXT_SPAN_KEY = Span.class.getName();

	static final String CONTEXT_SPAN_IN_SCOPE_KEY = Tracer.SpanInScope.class.getName();

	private static final Propagator.Getter<JobDataMap> GETTER = (carrier, key) -> {
		Object value = carrier.get(key);
		if (value instanceof String) {
			return (String) value;
		}
		return null;
	};

	private final Tracer tracer;

	private final Propagator propagator;

	TracingJobListener(Tracer tracer, Propagator propagator) {
		this.tracer = tracer;
		this.propagator = propagator;
	}

	@Override
	public String getName() {
		return getClass().getName();
	}

	@Override
	public void triggerFired(Trigger trigger, JobExecutionContext context) {
		Span nextSpan = propagator.extract(context.getMergedJobDataMap(), GETTER);
		Span span = nextSpan.name(context.getTrigger().getJobKey().toString()).tag(TRIGGER_TAG_KEY,
				context.getTrigger().getKey().toString());
		context.put(CONTEXT_SPAN_KEY, span);
		context.put(CONTEXT_SPAN_IN_SCOPE_KEY, tracer.withSpanInScope(span.start()));
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
		closeTrace(context);
	}

	@Override
	public void jobToBeExecuted(JobExecutionContext context) {

	}

	@Override
	public void jobExecutionVetoed(JobExecutionContext context) {
		closeTrace(context);
	}

	@Override
	public void jobWasExecuted(JobExecutionContext context, JobExecutionException jobException) {
	}

	private void closeTrace(JobExecutionContext context) {
		Object spanInScope = context.get(CONTEXT_SPAN_IN_SCOPE_KEY);
		Object span = context.get(CONTEXT_SPAN_KEY);
		if (spanInScope instanceof Tracer.SpanInScope) {
			((Tracer.SpanInScope) spanInScope).close();
		}
		if (span instanceof Span) {
			((Span) span).finish();
		}
	}

}
