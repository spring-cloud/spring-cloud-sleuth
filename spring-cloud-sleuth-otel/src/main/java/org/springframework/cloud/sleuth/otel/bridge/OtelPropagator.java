package org.springframework.cloud.sleuth.otel.bridge;

import java.util.List;

import io.grpc.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.TracingContextUtils;

import org.springframework.cloud.sleuth.api.TraceContext;
import org.springframework.cloud.sleuth.api.propagation.Propagator;

public class OtelPropagator implements Propagator {

	private final TextMapPropagator propagator;

	public OtelPropagator(ContextPropagators propagation) {
		this.propagator = propagation.getTextMapPropagator();
	}

	@Override
	public List<String> fields() {
		return this.propagator.fields();
	}

	@Override
	public <C> void inject(TraceContext traceContext, C carrier, Setter<C> setter) {
		Context context = OtelTraceContext.toOtelContext(traceContext);
		this.propagator.inject(context, carrier, setter::set);
	}

	@Override
	public <C> TraceContext extract(C carrier, Getter<C> getter) {
		Context extracted = this.propagator.extract(Context.current(), carrier, getter::get);
		Span span = TracingContextUtils.getSpanWithoutDefault(extracted);
		return new OtelTraceContext(span);
	}

}
