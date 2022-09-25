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

package org.springframework.cloud.sleuth.instrument.jdbc;

import org.springframework.cloud.sleuth.docs.DocumentedSpan;
import org.springframework.cloud.sleuth.docs.EventValue;
import org.springframework.cloud.sleuth.docs.TagKey;

enum SleuthJdbcSpan implements DocumentedSpan {

	/**
	 * Span created when a JDBC query gets executed.
	 */
	JDBC_QUERY_SPAN {
		@Override
		public String getName() {
			return "%s";
		}

		@Override
		public TagKey[] getTagKeys() {
			return QueryTags.values();
		}

		@Override
		public EventValue[] getEvents() {
			return QueryEvents.values();
		}

		@Override
		public String prefix() {
			return "jdbc.";
		}
	},

	/**
	 * Span created when working with JDBC result set.
	 */
	JDBC_RESULT_SET_SPAN {
		@Override
		public String getName() {
			return "result-set";
		}

		@Override
		public TagKey[] getTagKeys() {
			return QueryTags.values();
		}

		@Override
		public EventValue[] getEvents() {
			return QueryEvents.values();
		}

		@Override
		public String prefix() {
			return "jdbc.";
		}
	},

	/**
	 * Span created when a JDBC connection takes place.
	 */
	JDBC_CONNECTION_SPAN {
		@Override
		public String getName() {
			return "connection";
		}

		@Override
		public TagKey[] getTagKeys() {
			return ConnectionTags.values();
		}

		@Override
		public String prefix() {
			return "jdbc.";
		}
	};

	enum ConnectionTags implements TagKey {

		/**
		 * Name of the JDBC datasource driver.
		 */
		DATASOURCE_DRIVER {
			@Override
			public String getKey() {
				return "jdbc.datasource.driver";
			}
		},

		/**
		 * Name of the JDBC datasource pool.
		 */
		DATASOURCE_POOL {
			@Override
			public String getKey() {
				return "jdbc.datasource.pool";
			}
		},

	}

	enum QueryTags implements TagKey {

		/**
		 * The SQL query value.
		 */
		QUERY {
			@Override
			public String getKey() {
				return "jdbc.query";
			}
		},

		/**
		 * Number of SQL rows.
		 */
		ROW_COUNT {
			@Override
			public String getKey() {
				return "jdbc.row-count";
			}
		}

	}

	enum QueryEvents implements EventValue {

		/**
		 * When the transaction gets committed.
		 */
		COMMIT {
			@Override
			public String getValue() {
				return "jdbc.commit";
			}
		},

		/**
		 * When the transaction gets rolled back.
		 */
		ROLLBACK {
			@Override
			public String getValue() {
				return "jdbc.rollback";
			}
		}

	}

}
