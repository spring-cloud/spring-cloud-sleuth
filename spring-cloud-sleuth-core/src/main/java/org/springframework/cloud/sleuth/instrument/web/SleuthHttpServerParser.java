/*
 * Copyright 2013-2018 the original author or authors.
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

import javax.servlet.http.HttpServletResponse;

import brave.SpanCustomizer;
import brave.http.HttpAdapter;
import brave.http.HttpClientParser;
import brave.http.HttpServerParser;
import org.springframework.cloud.sleuth.ErrorParser;
import org.springframework.cloud.sleuth.TraceKeys;

/**
 * An {@link HttpClientParser} that behaves like Sleuth in versions 1.x
 *
 * @author Marcin Grzejszczak
 * @since 2.0.0
 */
class SleuthHttpServerParser extends HttpServerParser {

	private final SleuthHttpClientParser clientParser;
	private final ErrorParser errorParser;
	private final TraceKeys traceKeys;

	SleuthHttpServerParser(TraceKeys traceKeys, ErrorParser errorParser) {
		this.clientParser = new SleuthHttpClientParser(traceKeys);
		this.errorParser = errorParser;
		this.traceKeys = traceKeys;
	}

	@Override protected <Req> String spanName(HttpAdapter<Req, ?> adapter,
			Req req) {
		return this.clientParser.spanName(adapter, req);
	}

	@Override public <Req> void request(HttpAdapter<Req, ?> adapter, Req req,
			SpanCustomizer customizer) {
		this.clientParser.request(adapter, req, customizer);
	}

	@Override
	protected void error(Integer httpStatus, Throwable error, SpanCustomizer customizer) {
		this.errorParser.parseErrorTags(customizer, error);
	}

	@Override
	public <Resp> void response(HttpAdapter<?, Resp> adapter, Resp res, Throwable error,
			SpanCustomizer customizer) {
		if (res == null) {
			error(null, error, customizer);
			return;
		}
		int httpStatus = adapter.statusCode(res);
		if (httpStatus == HttpServletResponse.SC_OK && error != null) {
			// Filter chain threw exception but the response status may not have been set
			// yet, so we have to guess.
			customizer.tag(this.traceKeys.getHttp().getStatusCode(),
					String.valueOf(HttpServletResponse.SC_INTERNAL_SERVER_ERROR));
		}
		// only tag valid http statuses
		else if (httpStatus >= 100 && (httpStatus < 200) || (httpStatus > 399)) {
			customizer.tag(this.traceKeys.getHttp().getStatusCode(),
					String.valueOf(httpStatus));
		}
		error(httpStatus, error, customizer);
	}
}