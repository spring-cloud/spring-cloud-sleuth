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

package org.springframework.cloud.sleuth.event;

import java.util.ArrayList;

import org.springframework.cloud.sleuth.Span;
import org.springframework.context.ApplicationListener;

/**
 * @author Spencer Gibb
 */
public class ArrayListSpanAccumulator implements ApplicationListener<SpanReleasedEvent> {

	private final ArrayList<Span> spans = new ArrayList<>();

	public ArrayListSpanAccumulator() {
	}

	@Override
	public void onApplicationEvent(SpanReleasedEvent event) {
		this.spans.add(event.getSpan());
	}

	public ArrayList<Span> getSpans() {
		return this.spans;
	}

	public boolean equals(Object o) {
		if (o == this)
			return true;
		if (!(o instanceof ArrayListSpanAccumulator))
			return false;
		final ArrayListSpanAccumulator other = (ArrayListSpanAccumulator) o;
		final Object this$spans = this.spans;
		final Object other$spans = other.spans;
		if (this$spans == null ? other$spans != null : !this$spans.equals(other$spans))
			return false;
		return true;
	}

	public int hashCode() {
		final int PRIME = 59;
		int result = 1;
		final Object $spans = this.spans;
		result = result * PRIME + ($spans == null ? 0 : $spans.hashCode());
		return result;
	}

	public String toString() {
		return "org.springframework.cloud.sleuth.event.ArrayListSpanAccumulator(spans="
				+ this.spans + ")";
	}
}
