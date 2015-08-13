/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.sleuth.instrument.zuul;

import static org.springframework.cloud.sleuth.Trace.NOT_SAMPLED_NAME;
import static org.springframework.cloud.sleuth.Trace.PARENT_ID_NAME;
import static org.springframework.cloud.sleuth.Trace.PROCESS_ID_NAME;
import static org.springframework.cloud.sleuth.Trace.SPAN_ID_NAME;
import static org.springframework.cloud.sleuth.Trace.SPAN_NAME_NAME;
import static org.springframework.cloud.sleuth.Trace.TRACE_ID_NAME;
import static org.springframework.cloud.sleuth.TraceContextHolder.getCurrentSpan;
import static org.springframework.cloud.sleuth.TraceContextHolder.isTracing;

import java.util.Map;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.event.ClientSentEvent;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.util.ReflectionUtils;

import com.netflix.zuul.ZuulFilter;
import com.netflix.zuul.context.RequestContext;

/**
 * @author Dave Syer
 *
 */
public class TracePreZuulFilter extends ZuulFilter implements
ApplicationEventPublisherAware {

	private ApplicationEventPublisher publisher;

	@Override
	public void setApplicationEventPublisher(ApplicationEventPublisher publisher) {
		this.publisher = publisher;
	}

	@Override
	public boolean shouldFilter() {
		return true;
	}

	@Override
	public Object run() {
		RequestContext ctx = RequestContext.getCurrentContext();
		Map<String, String> response = ctx.getZuulRequestHeaders();
		// N.B. this will only work with the simple host filter (not ribbon) unless you set hystrix.execution.isolation.strategy=SEMAPHORE
		if (getCurrentSpan() == null) {
			setHeader(response, NOT_SAMPLED_NAME, "");
			return null;
		}
		try {
			setHeader(response, SPAN_ID_NAME, getCurrentSpan().getSpanId());
			setHeader(response, TRACE_ID_NAME, getCurrentSpan().getTraceId());
			setHeader(response, SPAN_NAME_NAME, getCurrentSpan().getName());
			setHeader(response, PARENT_ID_NAME, getParentId(getCurrentSpan()));
			setHeader(response, PROCESS_ID_NAME, getCurrentSpan().getProcessId());
			publish(new ClientSentEvent(this, getCurrentSpan()));
		}
		catch (Exception ex) {
			ReflectionUtils.rethrowRuntimeException(ex);
		}
		return null;
	}

	private String getParentId(Span span) {
		return span.getParents() != null && !span.getParents().isEmpty() ? span
				.getParents().get(0) : null;
	}

	public void setHeader(Map<String, String> request, String name, String value) {
		if (value != null && !request.containsKey(name) && isTracing()) {
			request.put(name, value);
		}
	}

	@Override
	public String filterType() {
		return "pre";
	}

	@Override
	public int filterOrder() {
		return 0;
	}

	private void publish(ApplicationEvent event) {
		if (this.publisher != null) {
			this.publisher.publishEvent(event);
		}
	}

}
