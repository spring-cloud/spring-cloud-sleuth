/*
 * Copyright 2015 the original author or authors.
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

package org.springframework.cloud.sleuth.stream;

import java.util.Collections;
import java.util.List;

import org.springframework.cloud.sleuth.Span;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Data transfer object for a collection of spans from a given host.
 *
 * @author Dave Syer
 * @since 1.0.0
 */
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public class Spans {

	private Host host;
	private List<Span> spans = Collections.emptyList();
	
	@SuppressWarnings("unused")
	private Spans() {
	}

	public Spans(Host host, List<Span> spans) {
		this.host = host;
		this.spans = spans;
	}

	public Host getHost() {
		return this.host;
	}

	public List<Span> getSpans() {
		return this.spans;
	}

	public void setHost(Host host) {
		this.host = host;
	}

	public void setSpans(List<Span> spans) {
		this.spans = spans;
	}
}
