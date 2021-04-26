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