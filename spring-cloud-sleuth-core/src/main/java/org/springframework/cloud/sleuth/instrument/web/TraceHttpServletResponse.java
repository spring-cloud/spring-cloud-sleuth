/*
 * Copyright 2013-2017 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.web;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.invoke.MethodHandles;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.sleuth.Span;

/**
 * We want to set SS as fast as possible after the response was sent back. The response
 * can be sent back by calling either an {@link ServletOutputStream} or {@link PrintWriter}.
 */
class TraceHttpServletResponse extends HttpServletResponseWrapper {

	private static final Log log = LogFactory.getLog(MethodHandles.lookup().lookupClass());

	private final Span span;

	TraceHttpServletResponse(HttpServletResponse response, Span span) {
		super(response);
		this.span = span;
	}

	@Override public void flushBuffer() throws IOException {
		if (log.isTraceEnabled()) {
			log.trace("Will annotate SS once the response is flushed");
		}
		SsLogSetter.annotateWithServerSendIfLogIsNotAlreadyPresent(this.span);
		super.flushBuffer();
	}

	@Override public ServletOutputStream getOutputStream() throws IOException {
		return new TraceServletOutputStream(super.getOutputStream(), this.span);
	}

	@Override public PrintWriter getWriter() throws IOException {
		return new TracePrintWriter(super.getWriter(), this.span);
	}
}
