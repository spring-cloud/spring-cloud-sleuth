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

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import com.mongodb.MongoSocketException;
import com.mongodb.RequestContext;
import com.mongodb.connection.ConnectionDescription;
import com.mongodb.connection.ConnectionId;
import com.mongodb.event.CommandFailedEvent;
import com.mongodb.event.CommandListener;
import com.mongodb.event.CommandStartedEvent;
import com.mongodb.event.CommandSucceededEvent;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import reactor.util.context.ContextView;

import org.springframework.cloud.sleuth.CurrentTraceContext;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.TraceContext;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.instrument.reactor.ReactorSleuth;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

/**
 * Altered the Brave MongoDb instrumentation code.
 * 
 * https://github.com/openzipkin/brave/blob/release-5.13.0/instrumentation/mongodb/src/main/java/brave/mongodb/TraceMongoCommandListener.java
 *
 * @author OpenZipkin Brave Authors
 */
final class TraceMongoCommandListener implements CommandListener {

	private static final Log log = LogFactory.getLog(TraceMongoCommandListener.class);

	// See https://docs.mongodb.com/manual/reference/command for the command reference
	static final Set<String> COMMANDS_WITH_COLLECTION_NAME = new LinkedHashSet<>(
			Arrays.asList("aggregate", "count", "distinct", "mapReduce", "geoSearch", "delete", "find", "findAndModify",
					"insert", "update", "collMod", "compact", "convertToCapped", "create", "createIndexes", "drop",
					"dropIndexes", "killCursors", "listIndexes", "reIndex"));

	private final Tracer tracer;

	private final CurrentTraceContext currentTraceContext;

	TraceMongoCommandListener(Tracer tracer, CurrentTraceContext currentTraceContext) {
		this.tracer = tracer;
		this.currentTraceContext = currentTraceContext;
	}

	@Override
	public void commandStarted(CommandStartedEvent event) {
		if (log.isDebugEnabled()) {
			log.debug("Instrumenting the command started event");
		}
		String databaseName = event.getDatabaseName();
		if ("admin".equals(databaseName)) {
			return; // don't trace commands like "endSessions"
		}

		RequestContext requestContext = event.getRequestContext();
		if (requestContext == null) {
			return;
		}
		Span parent = ReactorSleuth.spanFromContext(this.tracer, this.currentTraceContext, context(requestContext));
		if (log.isDebugEnabled()) {
			log.debug("Found the following span passed from the Reactor context [" + parent + "]");
		}
		Span.Builder childSpanBuilder = this.tracer.spanBuilder();
		if (parent != null) {
			childSpanBuilder.setParent(parent.context());
		}

		String commandName = event.getCommandName();
		BsonDocument command = event.getCommand();
		String collectionName = getCollectionName(command, commandName);

		childSpanBuilder.name(getSpanName(commandName, collectionName)).kind(Span.Kind.CLIENT)
				.remoteServiceName("mongodb-" + databaseName).tag("mongodb.command", commandName);

		if (collectionName != null) {
			childSpanBuilder.tag("mongodb.collection", collectionName);
		}

		ConnectionDescription connectionDescription = event.getConnectionDescription();
		if (connectionDescription != null) {
			ConnectionId connectionId = connectionDescription.getConnectionId();
			if (connectionId != null) {
				childSpanBuilder.tag("mongodb.cluster_id", connectionId.getServerId().getClusterId().getValue());
			}

			try {
				InetSocketAddress socketAddress = connectionDescription.getServerAddress().getSocketAddress();
				childSpanBuilder.remoteIpAndPort(socketAddress.getAddress().getHostAddress(), socketAddress.getPort());
			}
			catch (MongoSocketException ignored) {

			}
		}

		Span childSpan = childSpanBuilder.start();
		requestContext.put(Span.class, childSpan);
		requestContext.put(TraceContext.class, childSpan.context());
		if (log.isDebugEnabled()) {
			log.debug("Created a child span  [" + childSpan
					+ "] for mongo instrumentation and put it in Reactor context");
		}
	}

	@NonNull
	private ContextView context(RequestContext requestContext) {
		return new ContextView() {
			@Override
			public <T> T get(Object key) {
				return requestContext.get(key);
			}

			@Override
			public <T> T getOrDefault(Object key, T defaultValue) {
				return requestContext.getOrDefault(key, defaultValue);
			}

			@Override
			public boolean hasKey(Object key) {
				return requestContext.hasKey(key);
			}

			@Override
			public int size() {
				return requestContext.size();
			}

			@Override
			public Stream<Map.Entry<Object, Object>> stream() {
				return requestContext.stream();
			}
		};
	}

	@Override
	public void commandSucceeded(CommandSucceededEvent event) {
		RequestContext requestContext = event.getRequestContext();
		if (requestContext == null) {
			return;
		}
		Span span = requestContext.getOrDefault(Span.class, null);
		if (span == null) {
			return;
		}
		if (log.isDebugEnabled()) {
			log.debug("Command succeeded - will close span [" + span + "]");
		}
		span.end();
		requestContext.delete(Span.class);
		requestContext.delete(TraceContext.class);
	}

	@Override
	public void commandFailed(CommandFailedEvent event) {
		RequestContext requestContext = event.getRequestContext();
		if (requestContext == null) {
			return;
		}
		Span span = requestContext.getOrDefault(Span.class, null);
		if (span == null) {
			return;
		}
		if (log.isDebugEnabled()) {
			log.debug("Command failed - will close span [" + span + "]");
		}
		span.error(event.getThrowable());
		span.end();
		requestContext.delete(Span.class);
		requestContext.delete(TraceContext.class);
	}

	@Nullable
	String getCollectionName(BsonDocument command, String commandName) {
		if (COMMANDS_WITH_COLLECTION_NAME.contains(commandName)) {
			String collectionName = getNonEmptyBsonString(command.get(commandName));
			if (collectionName != null) {
				return collectionName;
			}
		}
		// Some other commands, like getMore, have a field like {"collection":
		// collectionName}.
		return getNonEmptyBsonString(command.get("collection"));
	}

	/**
	 * @return trimmed string from {@code bsonValue} or null if the trimmed string was
	 * empty or the value wasn't a string
	 */
	@Nullable
	static String getNonEmptyBsonString(BsonValue bsonValue) {
		if (bsonValue == null || !bsonValue.isString()) {
			return null;
		}
		String stringValue = bsonValue.asString().getValue().trim();
		return stringValue.isEmpty() ? null : stringValue;
	}

	static String getSpanName(String commandName, @Nullable String collectionName) {
		if (collectionName == null) {
			return commandName;
		}
		return commandName + " " + collectionName;
	}

}
