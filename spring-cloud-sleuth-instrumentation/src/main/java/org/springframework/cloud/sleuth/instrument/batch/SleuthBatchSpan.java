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

package org.springframework.cloud.sleuth.instrument.batch;

import org.springframework.cloud.sleuth.docs.DocumentedSpan;
import org.springframework.cloud.sleuth.docs.TagKey;

enum SleuthBatchSpan implements DocumentedSpan {

	/**
	 * Span created around a Job execution.
	 */
	BATCH_JOB_SPAN {
		@Override
		public String getName() {
			return "%s";
		}

		@Override
		public TagKey[] getTagKeys() {
			return JobTags.values();
		}

	},

	/**
	 * Span created around a Job execution.
	 */
	BATCH_STEP_SPAN {
		@Override
		public String getName() {
			return "%s";
		}

		@Override
		public TagKey[] getTagKeys() {
			return StepTags.values();
		}

	};

	enum JobTags implements TagKey {

		/**
		 * Name of the Spring Batch job.
		 */
		JOB_NAME {
			@Override
			public String getKey() {
				return "batch.job.name";
			}
		},

		/**
		 * ID of the Spring Batch job instance.
		 */
		JOB_INSTANCE_ID {
			@Override
			public String getKey() {
				return "batch.job.instanceId";
			}
		},

		/**
		 * ID of the Spring Batch execution.
		 */
		JOB_EXECUTION_ID {
			@Override
			public String getKey() {
				return "batch.job.executionId";
			}
		},

	}

	enum StepTags implements TagKey {

		/**
		 * Name of the Spring Batch job.
		 */
		STEP_NAME {
			@Override
			public String getKey() {
				return "batch.step.name";
			}
		},

		/**
		 * ID of the Spring Batch execution.
		 */
		STEP_EXECUTION_ID {
			@Override
			public String getKey() {
				return "batch.step.executionId";
			}
		},

		/**
		 * Type of the Spring Batch job.
		 */
		STEP_TYPE {
			@Override
			public String getKey() {
				return "batch.step.type";
			}
		},

		/**
		 * ID of the Spring Batch execution.
		 */
		JOB_EXECUTION_ID {
			@Override
			public String getKey() {
				return "batch.job.executionId";
			}
		},

	}

}
