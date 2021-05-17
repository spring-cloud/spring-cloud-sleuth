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

package org.springframework.cloud.sleuth.instrument.task;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanAndScope;
import org.springframework.cloud.sleuth.ThreadLocalSpan;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.task.listener.TaskExecutionListener;
import org.springframework.cloud.task.repository.TaskExecution;
import org.springframework.core.Ordered;

/**
 * Sets the span upon starting and closes it upon ending a task.
 *
 * @author Marcin Grzejszczak
 * @since 3.1.0
 */
public class TraceTaskExecutionListener implements TaskExecutionListener, Ordered {

	private static final Log log = LogFactory.getLog(TraceTaskExecutionListener.class);

	private final Tracer tracer;

	private final ThreadLocalSpan threadLocalSpan;

	private final String projectName;

	public TraceTaskExecutionListener(Tracer tracer, String projectName) {
		this.tracer = tracer;
		this.threadLocalSpan = new ThreadLocalSpan(tracer);
		this.projectName = projectName;
	}

	@Override
	public void onTaskStartup(TaskExecution taskExecution) {
		Span span = SleuthTaskSpan.TASK_EXECUTION_LISTENER_SPAN.wrap(this.tracer.nextSpan()).name(this.projectName)
				.start();
		this.threadLocalSpan.set(span);
		if (log.isDebugEnabled()) {
			log.debug("Put the span [" + span + "] to thread local");
		}
	}

	@Override
	public void onTaskEnd(TaskExecution taskExecution) {
		SpanAndScope spanAndScope = this.threadLocalSpan.get();
		Span span = spanAndScope.getSpan();
		span.end();
		spanAndScope.getScope().close();
		if (log.isDebugEnabled()) {
			log.debug("Removed the [" + span + "] from thread local");
		}
	}

	@Override
	public void onTaskFailed(TaskExecution taskExecution, Throwable throwable) {
		SpanAndScope spanAndScope = this.threadLocalSpan.get();
		Span span = spanAndScope.getSpan();
		span.error(throwable);
		span.end();
		spanAndScope.getScope().close();
		if (log.isDebugEnabled()) {
			log.debug("Removed the [" + span + "] from thread local and added error");
		}
	}

	@Override
	public int getOrder() {
		return Ordered.HIGHEST_PRECEDENCE;
	}

}
