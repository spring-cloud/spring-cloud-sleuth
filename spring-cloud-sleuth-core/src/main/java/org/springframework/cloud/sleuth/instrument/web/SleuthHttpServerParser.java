/*
 * Copyright 2013-2019 the original author or authors.
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

import javax.servlet.http.HttpServletResponse;

import brave.ErrorParser;
import brave.SpanCustomizer;
import brave.http.HttpAdapter;
import brave.http.HttpClientParser;
import brave.http.HttpServerParser;

/**
 * An {@link HttpClientParser} that behaves like Sleuth in versions 1.x.
 *
 * @author Marcin Grzejszczak
 * @since 2.0.0
 */
class SleuthHttpServerParser extends HttpServerParser {

	private static final String STATUS_CODE_KEY = "http.status_code";

	private final SleuthHttpClientParser clientParser;

	private final ErrorParser errorParser;

	SleuthHttpServerParser(TraceKeys traceKeys, ErrorParser errorParser) {
		this.clientParser = new SleuthHttpClientParser(traceKeys);
		this.errorParser = errorParser;
	}

	@Override
	protected ErrorParser errorParser() {
		return this.errorParser;
	}

	@Override
	protected <Req> String spanName(HttpAdapter<Req, ?> adapter, Req req) {
		return this.clientParser.spanName(adapter, req);
	}

	@Override
	public <Req> void request(HttpAdapter<Req, ?> adapter, Req req,
			SpanCustomizer customizer) {
		this.clientParser.request(adapter, req, customizer);
	}

	@Override
	public <Resp> void response(HttpAdapter<?, Resp> adapter, Resp res, Throwable error,
			SpanCustomizer customizer) {
		if (res == null) {
			error(null, error, customizer);
			return;
		}
		Integer httpStatus = adapter.statusCode(res);
		if (httpStatus == null) {
			error(httpStatus, error, customizer);
			return;
		}
		if (httpStatus == HttpServletResponse.SC_OK && error != null) {
			// Filter chain threw exception but the response status may not have been set
			// yet, so we have to guess.
			customizer.tag(STATUS_CODE_KEY,
					String.valueOf(HttpServletResponse.SC_INTERNAL_SERVER_ERROR));
		}
		// only tag valid http statuses
		else if (httpStatus >= 100 && (httpStatus < 200) || (httpStatus > 399)) {
			customizer.tag(STATUS_CODE_KEY, String.valueOf(httpStatus));
		}
		error(httpStatus, error, customizer);
	}

}
