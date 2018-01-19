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
package integration;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.logging.Log;

import zipkin2.Span;
import zipkin2.reporter.Reporter;

/**
 * Span Collector that logs spans and adds Spans to a list
 *
 * @author Marcin Grzejszczak
 */
public class IntegrationTestZipkinSpanReporter implements Reporter<Span> {

	private static final Log log = org.apache.commons.logging.LogFactory
			.getLog(IntegrationTestZipkinSpanReporter.class);

	public List<Span> hashedSpans = Collections.synchronizedList(new LinkedList<>());

	@Override
	public void report(Span span) {
		log.debug(span);
		this.hashedSpans.add(span);
	}

}
