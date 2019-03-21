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

import org.springframework.cloud.stream.annotation.Output;
import org.springframework.messaging.MessageChannel;

/**
 * Defines a message channel for instrumented applications to use to send span data to a
 * message broker. The channel accepts data in the form of {@link Spans} to buffer
 * multiple actual span instances in a single message. A client app may occasionally drop
 * spans, and if it does it should attempt to account for and report the number dropped.
 *
 * @author Dave Syer
 * @since 1.0.0
 *
 * @see SleuthSink
 * @deprecated Please use spring-cloud-sleuth-zipkin2 to report spans to Zipkin
 */
@Deprecated
public interface SleuthSource {

	String OUTPUT = "sleuth";

	@Output(SleuthSource.OUTPUT)
	MessageChannel output();

}