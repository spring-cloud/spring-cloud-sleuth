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

package org.springframework.cloud.sleuth.instrument.web.client.feign;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanInjector;
import org.springframework.util.StringUtils;

import feign.RequestTemplate;

/**
 * Span injector that injects tracing info to {@link RequestTemplate}
 *
 * @author Marcin Grzejszczak
 *
 * @since 1.0.0
 */
class FeignRequestTemplateInjector implements SpanInjector {

	@Override
	public <T> void inject(Span span, T carrier) {
		if (!(carrier instanceof RequestTemplate)) {
			return;
		}
		RequestTemplate template = (RequestTemplate) carrier;
		if (span == null) {
			setHeader(template, Span.NOT_SAMPLED_NAME, "true");
			return;
		}
		template.header(Span.TRACE_ID_NAME, Span.idToHex(span.getTraceId()));
		setHeader(template, Span.SPAN_NAME_NAME, span.getName());
		setHeader(template, Span.SPAN_ID_NAME, Span.idToHex(span.getSpanId()));
		if (!span.isExportable()) {
			setHeader(template, Span.NOT_SAMPLED_NAME, "true");
		}
		Long parentId = getParentId(span);
		if (parentId != null) {
			setHeader(template, Span.PARENT_ID_NAME, Span.idToHex(parentId));
		}
		setHeader(template, Span.PROCESS_ID_NAME, span.getProcessId());
	}

	private Long getParentId(Span span) {
		return !span.getParents().isEmpty() ? span.getParents().get(0) : null;
	}

	protected void setHeader(RequestTemplate request, String name, String value) {
		if (StringUtils.hasText(value) && !request.headers().containsKey(name)) {
			request.header(name, value);
		}
	}
}
