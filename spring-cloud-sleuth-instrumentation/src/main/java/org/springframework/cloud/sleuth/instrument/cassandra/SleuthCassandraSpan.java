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

package org.springframework.cloud.sleuth.instrument.cassandra;

import org.springframework.cloud.sleuth.docs.DocumentedSpan;
import org.springframework.cloud.sleuth.docs.EventValue;
import org.springframework.cloud.sleuth.docs.TagKey;

enum SleuthCassandraSpan implements DocumentedSpan {

	/**
	 * Span created around CqlSession executions.
	 */
	CASSANDRA_SPAN {
		@Override
		public String getName() {
			return "%s";
		}

		@Override
		public TagKey[] getTagKeys() {
			return Tags.values();
		}

		@Override
		public EventValue[] getEvents() {
			return Events.values();
		}

		@Override
		public String prefix() {
			return "cassandra.";
		}
	};

	enum Tags implements TagKey {

		/**
		 * Name of the Cassandra keyspace.
		 */
		KEYSPACE_NAME {
			@Override
			public String getKey() {
				return "cassandra.keyspace";
			}
		},

		/**
		 * A tag containing error that occurred for the given node.
		 */
		NODE_ERROR_TAG {
			@Override
			public String getKey() {
				return "cassandra.node[%s].error";
			}
		},

		/**
		 * A tag containing Cassandra CQL.
		 */
		CQL_TAG {
			@Override
			public String getKey() {
				return "cassandra.cql";
			}
		},

	}

	enum Events implements EventValue {

		/**
		 * Set whenever an error occurred for the given node.
		 */
		NODE_ERROR {
			@Override
			public String getValue() {
				return "cassandra.node.error";
			}
		},

		/**
		 * Set when a success occurred for the session processing.
		 */
		NODE_SUCCESS {
			@Override
			public String getValue() {
				return "cassandra.node.success";
			}
		}

	}

}
