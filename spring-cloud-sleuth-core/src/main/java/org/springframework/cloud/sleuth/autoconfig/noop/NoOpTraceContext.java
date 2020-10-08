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

package org.springframework.cloud.sleuth.autoconfig.noop;

import java.util.Collections;
import java.util.List;

import org.springframework.cloud.sleuth.api.TraceContext;

class NoOpTraceContext implements TraceContext {

	@Override
	public long traceIdHigh() {
		return 0;
	}

	@Override
	public long traceId() {
		return 0;
	}

	@Override
	public long localRootId() {
		return 0;
	}

	@Override
	public boolean isLocalRoot() {
		return false;
	}

	@Override
	public Long parentId() {
		return 0L;
	}

	@Override
	public long parentIdAsLong() {
		return 0;
	}

	@Override
	public long spanId() {
		return 0;
	}

	@Override
	public boolean shared() {
		return false;
	}

	@Override
	public List<Object> extra() {
		return Collections.emptyList();
	}

	@Override
	public <T> T findExtra(Class<T> type) {
		return null;
	}

	@Override
	public Builder toBuilder() {
		return new Builder() {
			@Override
			public Builder traceIdHigh(long traceIdHigh) {
				return this;
			}

			@Override
			public Builder traceId(long traceId) {
				return this;
			}

			@Override
			public Builder parentId(long parentId) {
				return this;
			}

			@Override
			public Builder parentId(Long parentId) {
				return this;
			}

			@Override
			public Builder spanId(long spanId) {
				return this;
			}

			@Override
			public Builder sampledLocal(boolean sampledLocal) {
				return this;
			}

			@Override
			public Builder sampled(boolean sampled) {
				return this;
			}

			@Override
			public Builder sampled(Boolean sampled) {
				return this;
			}

			@Override
			public Builder debug(boolean debug) {
				return this;
			}

			@Override
			public Builder shared(boolean shared) {
				return this;
			}

			@Override
			public Builder clearExtra() {
				return this;
			}

			@Override
			public Builder addExtra(Object extra) {
				return this;
			}

			@Override
			public TraceContext build() {
				return new NoOpTraceContext();
			}
		};
	}

	@Override
	public String traceIdString() {
		return "";
	}

	@Override
	public String parentIdString() {
		return "";
	}

	@Override
	public String localRootIdString() {
		return "";
	}

	@Override
	public String spanIdString() {
		return "";
	}

	@Override
	public Boolean sampled() {
		return null;
	}

	@Override
	public boolean sampledLocal() {
		return false;
	}

	@Override
	public boolean debug() {
		return false;
	}

}
