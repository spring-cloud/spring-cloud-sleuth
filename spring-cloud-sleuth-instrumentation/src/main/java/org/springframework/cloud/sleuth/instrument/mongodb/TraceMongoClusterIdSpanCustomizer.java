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

package org.springframework.cloud.sleuth.instrument.mongodb;

import com.mongodb.connection.ConnectionDescription;
import com.mongodb.connection.ConnectionId;
import com.mongodb.event.CommandStartedEvent;

import org.springframework.cloud.sleuth.Span;

public class TraceMongoClusterIdSpanCustomizer implements TraceMongoSpanCustomizer {

	public void customizeCommandStartSpan(CommandStartedEvent event, Span.Builder spanBuilder) {
		ConnectionDescription connectionDescription = event.getConnectionDescription();
		if (connectionDescription != null) {
			ConnectionId connectionId = connectionDescription.getConnectionId();
			if (connectionId != null) {
				spanBuilder.tag("mongodb.cluster_id", connectionId.getServerId().getClusterId().getValue());
			}
		}
	}

}
