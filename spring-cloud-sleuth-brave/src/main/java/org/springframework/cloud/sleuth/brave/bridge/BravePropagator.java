package org.springframework.cloud.sleuth.brave.bridge;

import java.util.List;

import brave.Tracing;
import brave.propagation.SamplingFlags;
import brave.propagation.TraceContextOrSamplingFlags;

import org.springframework.cloud.sleuth.api.Span;
import org.springframework.cloud.sleuth.api.TraceContext;
import org.springframework.cloud.sleuth.api.propagation.Propagator;

public class BravePropagator implements Propagator {

	private final Tracing tracing;

	public BravePropagator(Tracing tracing) {
		this.tracing = tracing;
	}

	@Override
	public List<String> fields() {
		return this.tracing.propagation().keys();
	}

	@Override
	public <C> void inject(TraceContext traceContext, C carrier, Setter<C> setter) {
		this.tracing.propagation().injector(setter::set).inject(BraveTraceContext.toBrave(traceContext), carrier);
	}

	@Override
	public <C> Span.Builder extract(C carrier, Getter<C> getter) {
		TraceContextOrSamplingFlags extract = this.tracing.propagation().extractor(getter::get).extract(carrier);
		if (extract.samplingFlags() == SamplingFlags.EMPTY) {
			this.tracing.tracer().nextSpan();
			return new BraveSpanBuilder(this.tracing.tracer());
		}
		return BraveSpanBuilder.toBuilder(this.tracing.tracer(), extract);
	}

}
