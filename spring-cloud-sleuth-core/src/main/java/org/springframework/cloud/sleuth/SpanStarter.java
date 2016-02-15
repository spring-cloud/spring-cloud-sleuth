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

package org.springframework.cloud.sleuth;

/**
 * Convenience class that helps in starting and closing spans
 * depending on whether they where created or continued
 *
 * @author Marcin Grzejszczak
 */
public class SpanStarter {

	private final Tracer tracer;

	public SpanStarter(Tracer tracer) {
		this.tracer = tracer;
	}

	public SpanHolder startOrContinueSpan(String spanName) {
		Span span = this.tracer.getCurrentSpan();
		return startOrContinueSpan(spanName, span);
	}

	public SpanHolder startOrContinueSpan(String spanName, Span span) {
		boolean created = false;
		if (span != null) {
			span = this.tracer.continueSpan(span);
		}
		else {
			span = this.tracer.startTrace(spanName);
			created = true;
		}
		return new SpanHolder(span, created);
	}

	public Span closeOrDetach(SpanHolder spanHolder) {
		if (spanHolder.created) {
			return this.tracer.close(spanHolder.span);
		}
		else {
			return this.tracer.detach(spanHolder.span);
		}
	}

	public Span closeIfCreated(SpanHolder spanHolder) {
		if (spanHolder.created) {
			return this.tracer.close(spanHolder.span);
		}
		return spanHolder.span;
	}
}
