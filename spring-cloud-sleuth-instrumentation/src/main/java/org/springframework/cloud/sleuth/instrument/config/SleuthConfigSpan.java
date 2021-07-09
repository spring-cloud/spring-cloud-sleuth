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

package org.springframework.cloud.sleuth.instrument.config;

import org.springframework.cloud.sleuth.docs.DocumentedSpan;
import org.springframework.cloud.sleuth.docs.TagKey;

enum SleuthConfigSpan implements DocumentedSpan {

	/**
	 * Span created around an EnvironmentRepository.
	 */
	CONFIG_SPAN {
		@Override
		public String getName() {
			return "find";
		}

		@Override
		public TagKey[] getTagKeys() {
			return Tags.values();
		}
	};

	enum Tags implements TagKey {

		/**
		 * Implementation of the EnvironmentRepository.
		 */
		ENVIRONMENT_CLASS {
			@Override
			public String getKey() {
				return "config.environment.class";
			}
		},

		/**
		 * Method executed on the EnvironmentRepository.
		 */
		ENVIRONMENT_METHOD {
			@Override
			public String getKey() {
				return "config.environment.method";
			}
		}

	}

}
