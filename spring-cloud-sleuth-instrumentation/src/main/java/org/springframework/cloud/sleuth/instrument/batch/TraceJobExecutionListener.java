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

class TraceJobExecutionListener implements JobExecutionListener {

	private final Tracer tracer;

	private static Map<JobExecution, SpanAndScope> SPANS = new ConcurrentHashMap<>();

	TraceJobExecutionListener(Tracer tracer) {
		this.tracer = tracer;
	}

	@Override
	public void beforeJob(JobExecution jobExecution) {
		Span span = this.tracer.nextSpan().name(jobExecution.getJobInstance().getJobName());
		span.tag("job.name", jobExecution.getJobInstance().getJobName());
		// TODO: How to add step type?
		Tracer.SpanInScope spanInScope = this.tracer.withSpan(span.start());
		SPANS.put(jobExecution, new SpanAndScope(span, spanInScope));
	}

	@Override
	public void afterJob(JobExecution jobExecution) {
		SpanAndScope spanAndScope = SPANS.remove(jobExecution);
		List<Throwable> throwables = jobExecution.getFailureExceptions();
		Span span = spanAndScope.getSpan();
		span.tag("status", jobExecution.getStatus().name());
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
