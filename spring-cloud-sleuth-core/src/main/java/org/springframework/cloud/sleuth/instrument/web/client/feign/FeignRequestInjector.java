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

package org.springframework.cloud.sleuth.instrument.web.client.feign;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanInjector;
import org.springframework.util.StringUtils;

import feign.Request;

/**
 * Span injector that injects tracing info to {@link Request} via {@link AtomicReference}
 * since {@link Request} is immutable.
 *
 * @author Marcin Grzejszczak
 *
 * @since 1.0.0
 */
class FeignRequestInjector implements SpanInjector<AtomicReference<Request>> {

	@Override
	public void inject(Span span, AtomicReference<Request> carrier) {
		String method = carrier.get().method();
		String url = carrier.get().url();
		Map<String, Collection<String>> headers = new HashMap<>(carrier.get().headers());
		byte[] body = carrier.get().body();
		Charset charset = carrier.get().charset();
		if (span == null) {
			setHeader(headers, Span.SAMPLED_NAME, Span.SPAN_NOT_SAMPLED);
			carrier.set(Request.create(method, url, headers, body, charset));
			return;
		}
		setHeader(headers, Span.TRACE_ID_NAME, Span.idToHex(span.getTraceId()));
		setHeader(headers, Span.SPAN_NAME_NAME, span.getName());
		setHeader(headers, Span.SPAN_ID_NAME, Span.idToHex(span.getSpanId()));
		setHeader(headers, Span.SAMPLED_NAME, span.isExportable() ?
				Span.SPAN_SAMPLED : Span.SPAN_NOT_SAMPLED);
		Long parentId = getParentId(span);
		if (parentId != null) {
			setHeader(headers, Span.PARENT_ID_NAME, Span.idToHex(parentId));
		}
		setHeader(headers, Span.PROCESS_ID_NAME, span.getProcessId());
		carrier.set(Request.create(method, url, headers, body, charset));
	}

	private Long getParentId(Span span) {
		return !span.getParents().isEmpty() ? span.getParents().get(0) : null;
	}

	protected void setHeader(Map<String, Collection<String>> headers, String name, String value) {
		if (StringUtils.hasText(value) && !headers.containsKey(name)) {
			List<String> list = new ArrayList<>();
			list.add(value);
			headers.put(name, list);
		}
	}
}
