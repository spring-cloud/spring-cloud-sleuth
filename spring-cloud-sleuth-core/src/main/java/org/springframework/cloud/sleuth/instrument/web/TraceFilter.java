/*
 * Copyright 2013-2015 the original author or authors.
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

import static org.springframework.util.StringUtils.hasText;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Random;
import java.util.regex.Pattern;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Span.SpanBuilder;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.event.ServerReceivedEvent;
import org.springframework.cloud.sleuth.event.ServerSentEvent;
import org.springframework.cloud.sleuth.instrument.TraceKeys;
import org.springframework.cloud.sleuth.sampler.IsTracingSampler;
import org.springframework.cloud.sleuth.trace.SpanContextHolder;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

/**
 * Filter that takes the value of the {@link Span#SPAN_ID_NAME} and
 * {@link Span#TRACE_ID_NAME} header from either request or response and uses them to
 * create a new span.
 *
 * <p>
 * In order to keep the size of spans manageable, this only add tags defined in
 * {@link TraceKeys}. If you need to add additional tags, such as headers subtype this and
 * override {@link #addRequestTags} or {@link #addResponseTags}.
 *
 * @see Tracer
 * @see TraceKeys
 * @see TraceWebAutoConfiguration#traceWebFilter(TraceFilter)
 *
 * @author Jakub Nabrdalik, 4financeIT
 * @author Tomasz Nurkiewicz, 4financeIT
 * @author Marcin Grzejszczak, 4financeIT
 * @author Spencer Gibb
 * @author Dave Syer
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class TraceFilter extends OncePerRequestFilter
		implements ApplicationEventPublisherAware {

	protected static final String TRACE_REQUEST_ATTR = TraceFilter.class.getName()
			+ ".TRACE";

	public static final Pattern DEFAULT_SKIP_PATTERN = Pattern.compile(
			"/api-docs.*|/autoconfig|/configprops|/dump|/info|/metrics.*|/mappings|/trace|/swagger.*|.*\\.png|.*\\.css|.*\\.js|.*\\.html|/favicon.ico|/hystrix.stream");

	private final Tracer tracer;
	private final TraceKeys traceKeys;
	private final Pattern skipPattern;
	private final Random random;

	private UrlPathHelper urlPathHelper = new UrlPathHelper();
	private ApplicationEventPublisher publisher;

	public TraceFilter(Tracer tracer, TraceKeys traceKeys) {
		this(tracer, traceKeys, DEFAULT_SKIP_PATTERN, new Random());
	}

	public TraceFilter(Tracer tracer, TraceKeys traceKeys, Pattern skipPattern,
			Random random) {
		this.tracer = tracer;
		this.traceKeys = traceKeys;
		this.skipPattern = skipPattern;
		this.random = random;
	}

	@Override
	public void setApplicationEventPublisher(ApplicationEventPublisher publisher) {
		this.publisher = publisher;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request,
			HttpServletResponse response, FilterChain filterChain)
					throws ServletException, IOException {
		SpanContextHolder.removeCurrentSpan();

		String uri = this.urlPathHelper.getPathWithinApplication(request);
		boolean skip = this.skipPattern.matcher(uri).matches()
				|| getHeader(request, response, Span.NOT_SAMPLED_NAME) != null;

		Span spanFromRequest = (Span) request.getAttribute(TRACE_REQUEST_ATTR);
		if (spanFromRequest != null) {
			this.tracer.continueSpan(spanFromRequest);
		}
		else if (skip) {
			addToResponseIfNotPresent(response, Span.NOT_SAMPLED_NAME, "");
		}

		String name = "http" + uri;
		if (spanFromRequest == null) {
			if (hasHeader(request, response, Span.TRACE_ID_NAME)) {
				long traceId = Span
						.fromHex(getHeader(request, response, Span.TRACE_ID_NAME));
				long spanId = hasHeader(request, response, Span.SPAN_ID_NAME)
						? Span.fromHex(getHeader(request, response, Span.SPAN_ID_NAME))
						: this.random.nextLong();

				SpanBuilder span = Span.builder().traceId(traceId).spanId(spanId);
				if (skip) {
					span.exportable(false);
				}
				String processId = getHeader(request, response, Span.PROCESS_ID_NAME);
				String parentName = getHeader(request, response, Span.SPAN_NAME_NAME);
				if (StringUtils.hasText(parentName)) {
					span.name(parentName);
				}
				else {
					span.name("parent/" + name);
				}
				if (StringUtils.hasText(processId)) {
					span.processId(processId);
				}
				if (hasHeader(request, response, Span.PARENT_ID_NAME)) {
					span.parent(Span
							.fromHex(getHeader(request, response, Span.PARENT_ID_NAME)));
				}
				span.remote(true);

				Span parent = span.build();
				spanFromRequest = this.tracer.joinTrace(name, parent);
				publish(new ServerReceivedEvent(this, parent, spanFromRequest));
				request.setAttribute(TRACE_REQUEST_ATTR, spanFromRequest);

			}
			else {
				if (skip) {
					spanFromRequest = this.tracer.startTrace(name,
							IsTracingSampler.INSTANCE);
				}
				else {
					spanFromRequest = this.tracer.startTrace(name);
				}
				request.setAttribute(TRACE_REQUEST_ATTR, spanFromRequest);
			}
		}

		Throwable exception = null;
		try {

			addRequestTags(request);
			filterChain.doFilter(request, response);

		}
		catch (Throwable e) {
			exception = e;
			throw e;
		}
		finally {
			if (isAsyncStarted(request) || request.isAsyncStarted()) {
				this.tracer.detach(spanFromRequest);
				// TODO: how to deal with response annotations and async?
				return;
			}
			if (skip) {
				addToResponseIfNotPresent(response, Span.NOT_SAMPLED_NAME, "");
			}
			if (spanFromRequest != null) {
				addResponseTags(response, exception);
				addResponseHeaders(response, spanFromRequest);
				if (spanFromRequest.hasSavedSpan()) {
					publish(new ServerSentEvent(this, spanFromRequest.getSavedSpan(),
							spanFromRequest));
				}
				// Double close to clean up the parent (remote span as well)
				this.tracer.close(this.tracer.close(spanFromRequest));
			}
		}
	}

	private void addResponseHeaders(HttpServletResponse response, Span span) {
		if (span != null) {
			response.addHeader(Span.SPAN_ID_NAME, Span.toHex(span.getSpanId()));
			response.addHeader(Span.TRACE_ID_NAME, Span.toHex(span.getTraceId()));
		}
	}

	private void publish(ApplicationEvent event) {
		if (this.publisher != null) {
			this.publisher.publishEvent(event);
		}
	}

	/** Override to add annotations not defined in {@link TraceKeys}. */
	protected void addRequestTags(HttpServletRequest request) {
		String uri = this.urlPathHelper.getPathWithinApplication(request);
		this.tracer.addTag(this.traceKeys.getHttp().getUrl(), getFullUrl(request));
		this.tracer.addTag(this.traceKeys.getHttp().getHost(), request.getServerName());
		this.tracer.addTag(this.traceKeys.getHttp().getPath(), uri);
		this.tracer.addTag(this.traceKeys.getHttp().getMethod(), request.getMethod());
		for (String name : this.traceKeys.getHttp().getHeaders()) {
			Enumeration<String> values = request.getHeaders(name);
			if (values.hasMoreElements()) {
				String key = this.traceKeys.getHttp().getPrefix() + name.toLowerCase();
				ArrayList<String> list = Collections.list(values);
				String value = list.size() == 1 ? list.get(0)
						: StringUtils.collectionToDelimitedString(list, ",", "'", "'");
				this.tracer.addTag(key, value);
			}
		}
	}

	/** Override to add annotations not defined in {@link TraceKeys}. */
	protected void addResponseTags(HttpServletResponse response, Throwable e) {
		int httpStatus = response.getStatus();
		if (httpStatus == HttpServletResponse.SC_OK && e != null) {
			// Filter chain threw exception but the response status may not have been set
			// yet, so we have to guess.
			this.tracer.addTag(this.traceKeys.getHttp().getStatusCode(),
					String.valueOf(HttpServletResponse.SC_INTERNAL_SERVER_ERROR));
		}
		else if ((httpStatus < 200) || (httpStatus > 299)) {
			this.tracer.addTag(this.traceKeys.getHttp().getStatusCode(),
					String.valueOf(response.getStatus()));
		}
	}

	private boolean hasHeader(HttpServletRequest request, HttpServletResponse response,
			String name) {
		String value = request.getHeader(name);
		return value != null || response.getHeader(name) != null;
	}

	private String getHeader(HttpServletRequest request, HttpServletResponse response,
			String name) {
		String value = request.getHeader(name);
		return value != null ? value : response.getHeader(name);
	}

	private void addToResponseIfNotPresent(HttpServletResponse response, String name,
			String value) {
		if (!hasText(response.getHeader(name))) {
			response.addHeader(name, value);
		}
	}

	@Override
	protected boolean shouldNotFilterAsyncDispatch() {
		return false;
	}

	private String getFullUrl(HttpServletRequest request) {
		StringBuffer requestURI = request.getRequestURL();
		String queryString = request.getQueryString();

		if (queryString == null) {
			return requestURI.toString();
		}
		else {
			return requestURI.append('?').append(queryString).toString();
		}
	}
}
