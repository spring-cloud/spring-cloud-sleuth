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

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;

class TraceStepExecutionListener implements StepExecutionListener {

	private final Tracer tracer;

	private static Map<StepExecution, SpanAndScope> SPANS = new ConcurrentHashMap<>();

	TraceStepExecutionListener(Tracer tracer) {
		this.tracer = tracer;
	}

	@Override
	public void beforeStep(StepExecution stepExecution) {
		Span span = this.tracer.nextSpan().name(stepExecution.getStepName());
		// TODO: How to add step type?
		Tracer.SpanInScope spanInScope = this.tracer.withSpan(span.start());
		SPANS.put(stepExecution, new SpanAndScope(span, spanInScope));
	}

	@Override
	public ExitStatus afterStep(StepExecution stepExecution) {
		SpanAndScope spanAndScope = SPANS.get(stepExecution);
		List<Throwable> throwables = stepExecution.getFailureExceptions();
		Span span = spanAndScope.span;
		span.tag("status", stepExecution.getStatus().name());
		Tracer.SpanInScope scope = spanAndScope.scope;
		if (!throwables.isEmpty()) {
			span.error(mergedThrowables(throwables));
		}
		span.end();
		scope.close();
		return stepExecution.getExitStatus();
	}

	private IllegalStateException mergedThrowables(List<Throwable> throwables) {
		return new IllegalStateException(throwables.stream().map(Throwable::getStackTrace).flatMap(Arrays::stream)
				.map(StackTraceElement::toString).collect(Collectors.joining("\n")));
	}

}
