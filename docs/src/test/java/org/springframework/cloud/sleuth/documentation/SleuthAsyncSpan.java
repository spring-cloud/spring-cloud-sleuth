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

package org.springframework.cloud.sleuth.documentation;

import org.springframework.cloud.sleuth.docs.DocumentedSpan;
import org.springframework.cloud.sleuth.docs.TagKey;

enum SleuthAsyncSpan implements DocumentedSpan {

	/**
	 * Span that wraps a @Async annotation. Either continues an existing one or creates a
	 * new one if there was no present one.
	 */
	ASYNC_ANNOTATION_SPAN {
		@Override
		public String getName() {
			return "%s";
		}

		@Override
		public TagKey[] getTagKeys() {
			return AsyncSpanTags.values();
		}

	},

	/**
	 * Test span.
	 */
	TEST_SPAN {
		@Override
		public String getName() {
			return "fixed";
		}

		@Override
		public TagKey[] getTagKeys() {
			return TestSpanTags.values();
		}

	};

	enum AsyncSpanTags implements TagKey {

		/**
		 * Class name where a method got annotated with @Async.
		 */
		CLASS {
			@Override
			public String getKey() {
				return "class";
			}
		},

		/**
		 * Method name that got annotated with @Async.
		 */
		METHOD {
			@Override
			public String getKey() {
				return "method";
			}
		}

	}

	enum TestSpanTags implements TagKey {

		/**
		 * Test foo
		 */
		FOO {
			@Override
			public String getKey() {
				return "foooooo";
			}
		}

	}

}
