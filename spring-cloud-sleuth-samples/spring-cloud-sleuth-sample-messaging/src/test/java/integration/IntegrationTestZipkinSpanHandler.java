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

package integration;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import brave.handler.MutableSpan;
import brave.handler.SpanHandler;
import brave.propagation.TraceContext;
import org.apache.commons.logging.Log;

/**
 * Span Collector that logs spans and adds Spans to a list.
 *
 * @author Marcin Grzejszczak
 */
public class IntegrationTestZipkinSpanHandler extends SpanHandler {

	private static final Log log = org.apache.commons.logging.LogFactory
			.getLog(IntegrationTestZipkinSpanHandler.class);

	public List<MutableSpan> spans = Collections.synchronizedList(new LinkedList<>());

	@Override
	public boolean end(TraceContext context, MutableSpan span, Cause cause) {
		log.debug(span);
		this.spans.add(span);
		return true;
	}

}
