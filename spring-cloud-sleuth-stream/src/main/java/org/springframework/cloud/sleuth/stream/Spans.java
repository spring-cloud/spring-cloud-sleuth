/*
 * Copyright 2015 the original author or authors.
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

package org.springframework.cloud.sleuth.stream;

import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.cloud.sleuth.Span;

/**
 * Data transfer object for a collection of spans from a given host.
 *
 * @author Dave Syer
 *
 */
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public class Spans {

	private Host host;
	private List<Span> spans = Collections.emptyList();

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

	public boolean equals(Object o) {
		if (o == this)
			return true;
		if (!(o instanceof Spans))
			return false;
		final Spans other = (Spans) o;
		if (!other.canEqual((Object) this))
			return false;
		final Object this$host = this.host;
		final Object other$host = other.host;
		if (this$host == null ? other$host != null : !this$host.equals(other$host))
			return false;
		final Object this$spans = this.spans;
		final Object other$spans = other.spans;
		if (this$spans == null ? other$spans != null : !this$spans.equals(other$spans))
			return false;
		return true;
	}

	public int hashCode() {
		final int PRIME = 59;
		int result = 1;
		final Object $host = this.host;
		result = result * PRIME + ($host == null ? 0 : $host.hashCode());
		final Object $spans = this.spans;
		result = result * PRIME + ($spans == null ? 0 : $spans.hashCode());
		return result;
	}

	protected boolean canEqual(Object other) {
		return other instanceof Spans;
	}

	public String toString() {
		return "org.springframework.cloud.sleuth.stream.Spans(host=" + this.host
				+ ", spans=" + this.spans + ")";
	}
}
