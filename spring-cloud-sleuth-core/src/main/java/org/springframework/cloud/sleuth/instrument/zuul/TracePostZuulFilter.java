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

import java.lang.invoke.MethodHandles;

import com.netflix.zuul.ZuulFilter;
import com.netflix.zuul.context.RequestContext;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.http.HttpStatus;

/**
 * A post request {@link ZuulFilter} that publishes an event upon start of the filtering
 *
 * @author Dave Syer
 * @since 1.0.0
 */
public class TracePostZuulFilter extends ZuulFilter {

	private static final Log log = LogFactory.getLog(MethodHandles.lookup().lookupClass());
	private static final String ERROR_STATUS_CODE = "error.status_code";

	private final Tracer tracer;

	public TracePostZuulFilter(Tracer tracer) {
		this.tracer = tracer;
	}

	@Override
	public boolean shouldFilter() {
		return getCurrentSpan() != null;
	}

	@Override
	public Object run() {
		// TODO: the client sent event should come from the client not the filter!
		setErrorStatusCodeIfMissing();
		getCurrentSpan().logEvent(Span.CLIENT_RECV);
		log.debug("Closing current client span " + getCurrentSpan() + "");
		this.tracer.close(getCurrentSpan());
		return null;
	}

	// we want to enforce the SendErrorFilter to process the error
	private void setErrorStatusCodeIfMissing() {
		RequestContext ctx = RequestContext.getCurrentContext();
		HttpStatus httpStatus = HttpStatus.valueOf(ctx.getResponseStatusCode());
		if (httpStatus.is4xxClientError() || httpStatus.is5xxServerError()) {
			if (!ctx.containsKey(ERROR_STATUS_CODE)) {
				ctx.set(ERROR_STATUS_CODE, ctx.getResponseStatusCode());
			}
		}
	}

	@Override
	public String filterType() {
		return "post";
	}

	@Override
	public int filterOrder() {
		return 0;
	}

	private Span getCurrentSpan() {
		return this.tracer.getCurrentSpan();
	}
}
