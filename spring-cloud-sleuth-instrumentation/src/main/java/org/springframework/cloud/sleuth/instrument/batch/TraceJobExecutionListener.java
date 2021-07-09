/*
 * Copyright 2018-2021 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.batch;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanAndScope;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.docs.AssertingSpan;

class TraceJobExecutionListener implements JobExecutionListener {

	private final Tracer tracer;

	private static final Map<JobExecution, SpanAndScope> SPANS = new ConcurrentHashMap<>();

	TraceJobExecutionListener(Tracer tracer) {
		this.tracer = tracer;
	}

	@Override
	public void beforeJob(JobExecution jobExecution) {
		Span span = SleuthBatchSpan.BATCH_JOB_SPAN.wrap(this.tracer.nextSpan())
				.name(jobExecution.getJobInstance().getJobName());
		Tracer.SpanInScope spanInScope = this.tracer.withSpan(span.start());
		SPANS.put(jobExecution, new SpanAndScope(span, spanInScope));
	}

	@Override
	public void afterJob(JobExecution jobExecution) {
		SpanAndScope spanAndScope = SPANS.remove(jobExecution);
		List<Throwable> throwables = jobExecution.getFailureExceptions();
		// @formatter:off
		AssertingSpan span = SleuthBatchSpan.BATCH_JOB_SPAN.wrap(spanAndScope.getSpan())
		.tag(SleuthBatchSpan.JobTags.JOB_NAME, jobExecution.getJobInstance().getJobName())
		.tag(SleuthBatchSpan.JobTags.JOB_INSTANCE_ID,
				String.valueOf(jobExecution.getJobInstance().getInstanceId()))
		.tag(SleuthBatchSpan.JobTags.JOB_EXECUTION_ID, String.valueOf(jobExecution.getId()));
		// formatter:on
		Tracer.SpanInScope scope = spanAndScope.getScope();
		if (!throwables.isEmpty()) {
			span.error(mergedThrowables(throwables));
		}
		span.end();
		scope.close();
	}

	private IllegalStateException mergedThrowables(List<Throwable> throwables) {
		return new IllegalStateException(
				throwables.stream().map(Throwable::toString).collect(Collectors.joining("\n")));
	}

}
