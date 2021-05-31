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

package org.springframework.cloud.sleuth.instrument.session;

import org.springframework.cloud.sleuth.docs.DocumentedSpan;
import org.springframework.cloud.sleuth.docs.TagKey;

enum SleuthSessionSpan implements DocumentedSpan {

	/**
	 * Span created when a new session has to be created.
	 */
	SESSION_CREATE_SPAN {
		@Override
		public String getName() {
			return "session.create";
		}
	},

	/**
	 * Span created when a new session is searched for.
	 */
	SESSION_FIND_SPAN {
		@Override
		public String getName() {
			return "session.find";
		}

		@Override
		public TagKey[] getTagKeys() {
			return Tags.values();
		}

		@Override
		public String prefix() {
			return "session.";
		}
	},

	/**
	 * Span created when a new session is saved.
	 */
	SESSION_SAVE_SPAN {
		@Override
		public String getName() {
			return "session.save";
		}
	},

	/**
	 * Span created when a session is deleted.
	 */
	SESSION_DELETE_SPAN {
		@Override
		public String getName() {
			return "session.delete";
		}
	};

	enum Tags implements TagKey {

		PRINCIPAL_NAME {
			@Override
			public String getKey() {
				return "session.principal.name";
			}
		},

		INDEX_NAME {
			@Override
			public String getKey() {
				return "session.index.name";
			}
		},

		INDEX_VALUE {
			@Override
			public String getKey() {
				return "session.index.value";
			}
		}

	}

}
