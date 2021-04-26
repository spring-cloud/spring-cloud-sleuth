package org.springframework.cloud.sleuth.instrument.messaging.rsocket;

import io.rsocket.core.RSocketServer;
import io.rsocket.plugins.RSocketInterceptor;
import org.springframework.boot.rsocket.server.RSocketServerCustomizer;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.propagation.Propagator;

public class TracingRSocketServerCustomizer implements RSocketServerCustomizer {

	final Propagator propagator;

	final Tracer tracer;

	public TracingRSocketServerCustomizer(Propagator propagator, Tracer tracer) {
		this.propagator = propagator;
		this.tracer = tracer;
	}

	@Override
	public void customize(RSocketServer rSocketServer) {
		rSocketServer.interceptors(ir -> ir
				.forResponder((RSocketInterceptor) rSocket -> new ResponderTracingRSocket(rSocket, propagator,
						new ByteBufGetter(), tracer))
				.forRequester((RSocketInterceptor) rSocket -> new RequesterTracingRSocket(rSocket, propagator,
						new ByteBufSetter(), tracer)));
	}

}
