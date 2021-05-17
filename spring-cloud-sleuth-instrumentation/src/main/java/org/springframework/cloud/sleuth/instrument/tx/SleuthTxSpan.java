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

package org.springframework.cloud.sleuth.instrument.tx;

import org.springframework.cloud.sleuth.docs.DocumentedSpan;
import org.springframework.cloud.sleuth.docs.EventValue;
import org.springframework.cloud.sleuth.docs.TagKey;

enum SleuthTxSpan implements DocumentedSpan {

	/**
	 * Span created when there was no previous transaction. If there was one, we will
	 * continue it unless propagation is required.
	 */
	TX_SPAN {
		@Override
		public String getName() {
			return "tx";
		}

		@Override
		public TagKey[] getTagKeys() {
			return Tags.values();
		}

		@Override
		public String prefix() {
			return "tx.";
		}
	};

	enum Tags implements TagKey {

		/**
		 * Name of the TransactionManager.
		 */
		TRANSACTION_MANAGER {
			@Override
			public String getKey() {
				return "tx.transaction-manager";
			}
		},

		/**
		 * Whether the transaction is read-only.
		 */
		READ_ONLY {
			@Override
			public String getKey() {
				return "tx.read-only";
			}
		},

		/**
		 * Transaction propagation level.
		 */
		PROPAGATION_LEVEL {
			@Override
			public String getKey() {
				return "tx.propagation-level";
			}
		},

		/**
		 * Transaction isolation level.
		 */
		ISOLATION_LEVEL {
			@Override
			public String getKey() {
				return "tx.isolation-level";
			}
		},

		/**
		 * Transaction timeout.
		 */
		TIMEOUT {
			@Override
			public String getKey() {
				return "tx.timeout";
			}
		},

		/**
		 * Transaction name.
		 */
		NAME {
			@Override
			public String getKey() {
				return "tx.name";
			}
		},

	}

	enum Events implements EventValue {

		/**
		 * Annotated after transaction commit.
		 */
		COMMIT {
			@Override
			public String getValue() {
				return "tx.commit";
			}
		},

		/**
		 * Annotated after transaction rollback.
		 */
		ROLLBACK {
			@Override
			public String getValue() {
				return "tx.rollback";
			}
		}

	}

}
