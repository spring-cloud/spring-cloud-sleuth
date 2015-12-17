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

package org.springframework.cloud.sleuth.template;

import org.springframework.cloud.sleuth.Trace;
import org.springframework.cloud.sleuth.TraceManager;
import org.springframework.cloud.sleuth.instrument.TraceDelegate;

/**
 * @author Spencer Gibb
 */
public class TraceTemplate implements TraceOperations  {

	private final TraceManager traceManager;

	public TraceTemplate(TraceManager traceManager) {
		this.traceManager = traceManager;
	}

	@Override
	public <T> T trace(final TraceCallback<T> callback) {
		if (this.traceManager.isTracing()) {
			DelegateCallback<T> delegate = new DelegateCallback<>(this.traceManager);
			Trace trace = delegate.startSpan();
			try {
				return callback.doInTrace(trace);
			} finally {
				this.traceManager.close(trace);
			}
		} else {
			return callback.doInTrace(null);
		}
	}

	class DelegateCallback<T> extends TraceDelegate<TraceCallback<T>> {

		public DelegateCallback(TraceManager traceManager) {
			super(traceManager, null);
		}

		@Override
		protected Trace startSpan() {
			return super.startSpan();
		}

		@Override
		public TraceCallback<T> getDelegate() {
			throw new UnsupportedOperationException();
		}
	}
}
