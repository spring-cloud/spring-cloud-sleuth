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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.TraceKeys;
import org.springframework.cloud.sleuth.Tracer;

import com.netflix.zuul.ZuulFilter;
import com.netflix.zuul.context.RequestContext;

/**8
 * A post request {@link ZuulFilter} that publishes an event upon start of the filtering
 *
 * @author Dave Syer
 * @since 1.0.0
 */
public class TracePostZuulFilter extends ZuulFilter {

	private static final Log log = LogFactory.getLog(MethodHandles.lookup().lookupClass());

	private final Tracer tracer;
	private final TraceKeys traceKeys;

	public TracePostZuulFilter(Tracer tracer, TraceKeys traceKeys) {
		this.tracer = tracer;
		this.traceKeys = traceKeys;
	}

	@Override
	public boolean shouldFilter() {
		return getCurrentSpan() != null;
	}

	@Override
	public Object run() {
		// TODO: the client sent event should come from the client not the filter!
		getCurrentSpan().logEvent(Span.CLIENT_RECV);
		if (log.isDebugEnabled()) {
			log.debug("Closing current client span " + getCurrentSpan() + "");
		}
		this.tracer.addTag(this.traceKeys.getHttp().getStatusCode(),
				String.valueOf(RequestContext.getCurrentContext().getResponse().getStatus()));
		this.tracer.close(getCurrentSpan());
		return null;
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
