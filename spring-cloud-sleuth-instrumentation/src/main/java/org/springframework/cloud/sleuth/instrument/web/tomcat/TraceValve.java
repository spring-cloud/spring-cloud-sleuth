/*
 * Copyright 2013-2021 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.web.tomcat;

import java.io.IOException;

import javax.servlet.ServletException;

import org.apache.catalina.Valve;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ValveBase;

import org.springframework.cloud.sleuth.CurrentTraceContext;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanCustomizer;
import org.springframework.cloud.sleuth.TraceContext;
import org.springframework.cloud.sleuth.http.HttpServerHandler;
import org.springframework.cloud.sleuth.instrument.web.servlet.HttpServletRequestWrapper;
import org.springframework.cloud.sleuth.instrument.web.servlet.HttpServletResponseWrapper;
import org.springframework.core.log.LogAccessor;

/**
 * A trace representation of a {@link Valve}.
 *
 * @author Marcin Grzejszczak
 * @since 3.1.0
 */
public class TraceValve extends ValveBase {

	private static final LogAccessor log = new LogAccessor(TraceValve.class);

	private final HttpServerHandler httpServerHandler;

	private final CurrentTraceContext currentTraceContext;

	public TraceValve(HttpServerHandler httpServerHandler, CurrentTraceContext currentTraceContext) {
		this.httpServerHandler = httpServerHandler;
		this.currentTraceContext = currentTraceContext;
		setAsyncSupported(true);
	}

	@Override
	public void invoke(Request request, Response response) throws IOException, ServletException {
		Exception ex = null;
		Span handleReceive = this.httpServerHandler
				.handleReceive(HttpServletRequestWrapper.create(request.getRequest()));
		if (log.isDebugEnabled()) {
			log.debug("Created a server receive span [" + handleReceive + "]");
		}
		request.setAttribute(SpanCustomizer.class.getName(), handleReceive);
		request.setAttribute(TraceContext.class.getName(), handleReceive.context());
		request.setAttribute(Span.class.getName(), handleReceive);
		try (CurrentTraceContext.Scope ws = this.currentTraceContext.maybeScope(handleReceive.context())) {
			Valve next = getNext();
			if (null == next) {
				// no next valve
				return;
			}
			next.invoke(request, response);
		}
		catch (Exception exception) {
			ex = exception;
			throw exception;
		}
		finally {
			this.httpServerHandler.handleSend(
					HttpServletResponseWrapper.create(request.getRequest(), response.getResponse(), ex), handleReceive);
			if (log.isDebugEnabled()) {
				log.debug("Handled send of span [" + handleReceive + "]");
			}
		}
	}

}
