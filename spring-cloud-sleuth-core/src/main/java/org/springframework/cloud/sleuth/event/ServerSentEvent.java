/*
 * Copyright 2013-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.sleuth.event;

import org.springframework.cloud.sleuth.Span;

/**
 * <b>ss</b> - Server Send. Annotated upon completion of request processing (when the response
 * got sent back to the client). If one subtracts the sr timestamp from this timestamp one
 * will receive the time needed by the server side to process the request.
 *
 * @author Spencer Gibb
 * @since 1.0.0
 *
 * @see ServerReceivedEvent
 */
@SuppressWarnings("serial")
public class ServerSentEvent extends SpanParentContainingEvent {

	public ServerSentEvent(Object source, Span span) {
		this(source, null, span);
	}

	public ServerSentEvent(Object source, Span parent, Span span) {
		super(source, parent, span);
	}
}