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

import com.github.kristofa.brave.LoggingSpanCollector;
import com.twitter.zipkin.gen.Span;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Span Collector that logs spans and adds Spans to a list
 *
 * @author Marcin Grzejszczak
 */
public class IntegrationTestSpanCollector extends LoggingSpanCollector {

	public List<Span> hashedSpans = Collections.<Span>synchronizedList(new LinkedList<Span>());

	@Override
	public void collect(Span span) {
		super.collect(span);
		hashedSpans.add(span);
	}

}
