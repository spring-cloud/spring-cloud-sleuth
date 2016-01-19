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

import com.netflix.zuul.ZuulFilter;
import com.netflix.zuul.context.RequestContext;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.TraceAccessor;
import org.springframework.cloud.sleuth.event.ClientSentEvent;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.util.ReflectionUtils;

import java.util.Map;

/**
 * @author Dave Syer
 *
 */
public class TracePreZuulFilter extends ZuulFilter implements
ApplicationEventPublisherAware {

	private ApplicationEventPublisher publisher;

	private final TraceAccessor accessor;

	public TracePreZuulFilter(TraceAccessor accessor) {
		this.accessor = accessor;
	}

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
		Span span = getCurrentSpan();
		if (span == null) {
			setHeader(response, Span.NOT_SAMPLED_NAME, "");
			return null;
		}
		try {
			setHeader(response, Span.SPAN_ID_NAME, span.getSpanId());
			setHeader(response, Span.TRACE_ID_NAME, span.getTraceId());
			setHeader(response, Span.SPAN_NAME_NAME, span.getName());
			setHeader(response, Span.PARENT_ID_NAME, getParentId(span));
			setHeader(response, Span.PROCESS_ID_NAME, span.getProcessId());
			// TODO: the client sent event should come from the client not the filter!
			publish(new ClientSentEvent(this, span));
		}
		catch (Exception ex) {
			ReflectionUtils.rethrowRuntimeException(ex);
		}
		return null;
	}

	private Span getCurrentSpan() {
		return this.accessor.getCurrentSpan();
	}

	private Long getParentId(Span span) {
		return !span.getParents().isEmpty() ? span
				.getParents().get(0) : null;
	}

	public void setHeader(Map<String, String> request, String name, String value) {
		if (value != null && !request.containsKey(name) && this.accessor.isTracing()) {
			request.put(name, value);
		}
	}
	public void setHeader(Map<String, String> request, String name, Long value) {
		setHeader(request, name, Span.IdConverter.toHex(value));
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
