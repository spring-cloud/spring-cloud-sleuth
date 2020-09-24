/*
 * Copyright 2013-2020 the original author or authors.
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

package org.springframework.cloud.sleuth.brave.autoconfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import brave.handler.MutableSpan;
import brave.handler.SpanHandler;
import brave.propagation.TraceContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.util.StringUtils;

/**
 * {@link SpanHandler} that ignores spans via names.
 *
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
class SpanIgnoringSpanHandler extends SpanHandler {

	private static final Log log = LogFactory.getLog(SpanIgnoringSpanHandler.class);

	private final SleuthProperties sleuthProperties;

	static final Map<String, Pattern> cache = new ConcurrentHashMap<>();

	SpanIgnoringSpanHandler(SleuthProperties sleuthProperties) {
		this.sleuthProperties = sleuthProperties;
	}

	@Override
	public boolean end(TraceContext context, MutableSpan span, Cause cause) {
		if (cause != Cause.FINISHED) {
			return true;
		}
		List<Pattern> spanNamesToIgnore = spanNamesToIgnore();
		String name = span.name();
		if (StringUtils.hasText(name) && spanNamesToIgnore.stream().anyMatch(p -> p.matcher(name).matches())) {
			if (log.isDebugEnabled()) {
				log.debug("Will ignore a span with name [" + name + "]");
			}
			return false;
		}
		return super.end(context, span, cause);
	}

	private List<Pattern> spanNamesToIgnore() {
		return spanNames().stream().map(regex -> cache.computeIfAbsent(regex, Pattern::compile))
				.collect(Collectors.toList());
	}

	private List<String> spanNames() {
		List<String> spanNamesToIgnore = new ArrayList<>(
				this.sleuthProperties.getSpanHandler().getSpanNamePatternsToSkip());
		spanNamesToIgnore.addAll(this.sleuthProperties.getSpanHandler().getAdditionalSpanNamePatternsToIgnore());
		return spanNamesToIgnore;
	}

}
