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

package org.springframework.cloud.sleuth.instrument.rxjava;

import rx.functions.Action;

import org.springframework.cloud.sleuth.docs.DocumentedSpan;
import org.springframework.cloud.sleuth.docs.TagKey;

enum SleuthRxJavaSpan implements DocumentedSpan {

	/**
	 * Span that wraps a Rx Java {@link Action}.
	 */
	RX_JAVA_TRACE_ACTION_SPAN {
		@Override
		public String getName() {
			return "rxjava";
		}

		@Override
		public TagKey[] getTagKeys() {
			return Tags.values();
		}

	};

	/**
	 * Tags related to RX Java.
	 *
	 * @author Marcin Grzejszczak
	 * @since 3.0.3
	 */
	enum Tags implements TagKey {

		/**
		 * Name of the thread.
		 */
		THREAD {
			@Override
			public String getKey() {
				return "thread";
			}
		}

	}

}
