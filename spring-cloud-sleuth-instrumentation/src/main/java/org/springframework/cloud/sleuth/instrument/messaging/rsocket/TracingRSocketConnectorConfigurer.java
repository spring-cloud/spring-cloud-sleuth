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

package org.springframework.cloud.sleuth.instrument.messaging.rsocket;

import io.rsocket.core.RSocketConnector;
import io.rsocket.plugins.RSocketInterceptor;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.propagation.Propagator;
import org.springframework.messaging.rsocket.RSocketConnectorConfigurer;
import org.springframework.stereotype.Component;

public class TracingRSocketConnectorConfigurer implements RSocketConnectorConfigurer {

	final Propagator propagator;

	final Tracer tracer;

	public TracingRSocketConnectorConfigurer(Propagator propagator, Tracer tracer) {
		this.propagator = propagator;
		this.tracer = tracer;
	}

	@Override
	public void configure(RSocketConnector rSocketConnector) {
		rSocketConnector.interceptors(ir -> ir
				.forResponder((RSocketInterceptor) rSocket -> new ResponderTracingRSocket(rSocket, propagator,
						new ByteBufGetter(), tracer))
				.forRequester((RSocketInterceptor) rSocket -> new RequesterTracingRSocket(rSocket, propagator,
						new ByteBufSetter(), tracer)));
	}

}
