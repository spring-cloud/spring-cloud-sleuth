package org.springframework.cloud.sleuth.brave.bridge;

import java.util.List;

import brave.Tracer;
import brave.propagation.Propagation;
import brave.propagation.SamplingFlags;
import brave.propagation.TraceContextOrSamplingFlags;

import org.springframework.cloud.sleuth.api.Span;
import org.springframework.cloud.sleuth.api.TraceContext;
import org.springframework.cloud.sleuth.api.propagation.Propagator;

public class BravePropagator implements Propagator {

	private final Propagation<String> propagation;

	private final Tracer tracer;

	public BravePropagator(Propagation.Factory factory, Tracer tracer) {
		this(factory.get(), tracer);
	}

	public BravePropagator(Propagation<String> propagation, Tracer tracer) {
		this.propagation = propagation;
		this.tracer = tracer;
	}

	@Override
	public List<String> fields() {
		return propagation.keys();
	}

	@Override
	public <C> void inject(TraceContext traceContext, C carrier, Setter<C> setter) {
		this.propagation.injector(setter::set).inject(BraveTraceContext.toBrave(traceContext), carrier);
	}

	@Override
	public <C> Span extract(C carrier, Getter<C> getter) {
		TraceContextOrSamplingFlags extract = propagation.extractor(getter::get).extract(carrier);
		if (extract.samplingFlags() == SamplingFlags.EMPTY) {
			return BraveSpan.fromBrave(this.tracer.nextSpan());
		}
		brave.Span nextSpan = this.tracer.nextSpan(extract);
		return BraveSpan.fromBrave(nextSpan);
	}

}
