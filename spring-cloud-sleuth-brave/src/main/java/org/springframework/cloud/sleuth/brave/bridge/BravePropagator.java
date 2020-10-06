package org.springframework.cloud.sleuth.brave.bridge;

import java.util.List;

import brave.propagation.Propagation;
import brave.propagation.SamplingFlags;
import brave.propagation.TraceContextOrSamplingFlags;

import org.springframework.cloud.sleuth.api.TraceContext;
import org.springframework.cloud.sleuth.api.propagation.Propagator;

public class BravePropagator implements Propagator {

	private final Propagation<String> propagation;

	public BravePropagator(Propagation.Factory factory) {
		this(factory.get());
	}

	public BravePropagator(Propagation<String> propagation) {
		this.propagation = propagation;
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
	public <C> TraceContext extract(C carrier, Getter<C> getter) {
		TraceContextOrSamplingFlags extract = propagation.extractor(getter::get).extract(carrier);
		if (extract.samplingFlags() == SamplingFlags.EMPTY) {
			return null;
		}
		return BraveTraceContext.fromBrave(extract.context());
	}

}
