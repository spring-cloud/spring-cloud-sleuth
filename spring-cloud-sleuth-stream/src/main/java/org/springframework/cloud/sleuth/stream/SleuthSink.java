/*
 * Copyright 2015 the original author or authors.
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

package org.springframework.cloud.sleuth.stream;

import org.springframework.cloud.stream.annotation.Input;
import org.springframework.messaging.SubscribableChannel;

/**
 * Defines a message channel for accepting and processing span data from remote,
 * instrumented applications. Span data comes into the channel in the form of
 * {@link Spans}, buffering multiple actual spans into a single payload.
 *
 * @author Dave Syer
 * @since 1.0.0
 *
 * @see SleuthSource
 * @deprecated Please use spring-cloud-sleuth-zipkin2 to report spans to Zipkin
 */
@Deprecated
public interface SleuthSink {

	String INPUT = "sleuth";

	@Input(SleuthSink.INPUT)
	SubscribableChannel input();
}
