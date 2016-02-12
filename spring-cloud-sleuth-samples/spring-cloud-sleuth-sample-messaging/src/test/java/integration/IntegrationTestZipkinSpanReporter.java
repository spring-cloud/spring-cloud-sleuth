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
package integration;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.springframework.cloud.sleuth.zipkin.ZipkinSpanReporter;
import zipkin.Span;

/**
 * Span Collector that logs spans and adds Spans to a list
 *
 * @author Marcin Grzejszczak
 */
public class IntegrationTestZipkinSpanReporter implements ZipkinSpanReporter {

	private static final Log log = org.apache.commons.logging.LogFactory
			.getLog(IntegrationTestZipkinSpanReporter.class);

	public Set<Span> hashedSpans = Collections.synchronizedSet(new HashSet<>());

	@Override
	public void report(Span span) {
		log.info("Received span [" + span + "]");
		Span spanToSave = span;
		if (this.hashedSpans.contains(span)) {
			Span storedSpan = this.hashedSpans.stream().filter(span1 -> span1.equals(span)).findFirst().get();
			spanToSave = new Span.Builder(storedSpan).merge(span).build();
		}
		this.hashedSpans.add(spanToSave);
	}

}
