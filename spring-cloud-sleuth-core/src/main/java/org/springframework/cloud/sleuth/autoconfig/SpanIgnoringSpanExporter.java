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

package org.springframework.cloud.sleuth.autoconfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.sleuth.api.exporter.ReportedSpan;
import org.springframework.cloud.sleuth.api.exporter.SpanExporter;
import org.springframework.util.StringUtils;

/**
 * {@link SpanExporter} that ignores spans via names.
 *
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
class SpanIgnoringSpanExporter implements SpanExporter {

	private static final Log log = LogFactory.getLog(SpanIgnoringSpanExporter.class);

	private final SleuthSpanExporterProperties sleuthSpanExporterProperties;

	static final Map<String, Pattern> cache = new ConcurrentHashMap<>();

	SpanIgnoringSpanExporter(SleuthSpanExporterProperties sleuthSpanExporterProperties) {
		this.sleuthSpanExporterProperties = sleuthSpanExporterProperties;
	}

	private List<Pattern> spanNamesToIgnore() {
		return spanNames().stream().map(regex -> cache.computeIfAbsent(regex, Pattern::compile))
				.collect(Collectors.toList());
	}

	private List<String> spanNames() {
		List<String> spanNamesToIgnore = new ArrayList<>(this.sleuthSpanExporterProperties.getSpanNamePatternsToSkip());
		spanNamesToIgnore.addAll(this.sleuthSpanExporterProperties.getAdditionalSpanNamePatternsToIgnore());
		return spanNamesToIgnore;
	}

	@Override
	public boolean export(ReportedSpan span) {
		List<Pattern> spanNamesToIgnore = spanNamesToIgnore();
		String name = span.name();
		if (StringUtils.hasText(name) && spanNamesToIgnore.stream().anyMatch(p -> p.matcher(name).matches())) {
			if (log.isDebugEnabled()) {
				log.debug("Will ignore a span with name [" + name + "]");
			}
			return false;
		}
		return true;
	}

}
