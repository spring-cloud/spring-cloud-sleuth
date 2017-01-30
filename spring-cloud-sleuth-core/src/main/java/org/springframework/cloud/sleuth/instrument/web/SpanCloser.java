/*
 * Copyright 2013-2016 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.web;

import java.lang.invoke.MethodHandles;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanReporter;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.http.HttpStatus;

/**
 * A helper class to close spans
 *
 * @author Marcin Grzejszczak
 */
class SpanCloser {

	private static final Log log = LogFactory.getLog(MethodHandles.lookup().lookupClass());

	private Tracer tracer;
	private SpanReporter spanReporter;
	// we want to share the logic of adding response tags between interceptor and filter
	// also the add response tags method is protected and we want it to be consistent between
	// filter / interceptors
	private TraceFilter traceFilter;
	private BeanFactory beanFactory;

	SpanCloser(Tracer tracer, SpanReporter spanReporter, TraceFilter traceFilter) {
		this.tracer = tracer;
		this.spanReporter = spanReporter;
		this.traceFilter = traceFilter;
	}

	SpanCloser(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}

	void detachOrCloseSpans(HttpServletRequest request,
			HttpServletResponse response, Span spanFromRequest, Throwable exception) {
		Span span = spanFromRequest;
		if (log.isDebugEnabled()) {
			log.debug("Will try to detach or close " + span + "");
		}
		if (span != null) {
			getTraceFilter().addResponseTags(response, exception);
			if (span.hasSavedSpan() && requestHasAlreadyBeenHandled(request)) {
				recordParentSpan(span.getSavedSpan());
			} else if (!requestHasAlreadyBeenHandled(request)) {
				if (log.isDebugEnabled()) {
					log.debug("Closing the span " + span + " since the request wasn't handled");
				}
				span = getTracer().close(span);
			}
			recordParentSpan(span);
			// in case of a response with exception status will close the span when exception dispatch is handled
			// checking if tracing is in progress due to async / different order of view controller processing
			if (httpStatusSuccessful(response) && getTracer().isTracing()) {
				if (log.isDebugEnabled()) {
					log.debug("Closing the span " + span + " since the response was successful");
				}
				getTracer().close(span);
			} else if (errorAlreadyHandled(request) && getTracer().isTracing()) {
				if (log.isDebugEnabled()) {
					log.debug(
							"Won't detach the span " + span + " since error has already been handled");
				}
			} else if (getTracer().isTracing()) {
				if (log.isDebugEnabled()) {
					log.debug("Detaching the span " + span + " since the response was unsuccessful");
				}
				getTracer().detach(span);
			}
		}
	}

	private void recordParentSpan(Span parent) {
		if (parent == null) {
			return;
		}
		if (parent.isRemote()) {
			if (log.isDebugEnabled()) {
				log.debug("Trying to send the parent span " + parent + " to Zipkin");
			}
			parent.stop();
			parent.logEvent(Span.SERVER_SEND);
			getSpanReporter().report(parent);
		} else {
			parent.logEvent(Span.SERVER_SEND);
		}
	}

	private boolean requestHasAlreadyBeenHandled(HttpServletRequest request) {
		return request.getAttribute(TraceRequestAttributes.HANDLED_SPAN_REQUEST_ATTR) != null;
	}

	private boolean errorAlreadyHandled(HttpServletRequest request) {
		return Boolean.valueOf(
				String.valueOf(request.getAttribute(TraceFilter.TRACE_ERROR_HANDLED_REQUEST_ATTR)));
	}

	private boolean httpStatusSuccessful(HttpServletResponse response) {
		if (response.getStatus() == 0) {
			return false;
		}
		HttpStatus.Series httpStatusSeries = HttpStatus.Series.valueOf(response.getStatus());
		return httpStatusSeries == HttpStatus.Series.SUCCESSFUL || httpStatusSeries == HttpStatus.Series.REDIRECTION;
	}


	private Tracer getTracer() {
		if (this.tracer == null) {
			this.tracer = this.beanFactory.getBean(Tracer.class);
		}
		return this.tracer;
	}

	private TraceFilter getTraceFilter() {
		if (this.traceFilter == null) {
			this.traceFilter = this.beanFactory.getBean(TraceFilter.class);
		}
		return this.traceFilter;
	}

	private SpanReporter getSpanReporter() {
		if (this.spanReporter == null) {
			this.spanReporter = this.beanFactory.getBean(SpanReporter.class);
		}
		return this.spanReporter;
	}
}
