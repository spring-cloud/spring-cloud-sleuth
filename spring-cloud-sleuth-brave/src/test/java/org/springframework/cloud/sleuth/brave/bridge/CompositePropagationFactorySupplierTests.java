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

package org.springframework.cloud.sleuth.brave.bridge;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import brave.internal.codec.HexCodec;
import brave.internal.propagation.StringPropagationAdapter;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.cloud.loadbalancer.support.SimpleObjectProvider;
import org.springframework.cloud.sleuth.brave.propagation.PropagationType;
import org.springframework.util.StringUtils;

class CompositePropagationFactorySupplierTests {

	@Test
	void should_pick_custom_registered_propagation_when_custom_mode_picked() {
		BeanFactory beanFactory = Mockito.mock(BeanFactory.class);
		Mockito.when(beanFactory.getBeanProvider(BraveBaggageManager.class))
				.thenReturn(new SimpleObjectProvider(new BraveBaggageManager()));
		Mockito.when(beanFactory.getBeanProvider(Propagation.Factory.class))
				.thenReturn(new SimpleObjectProvider(new CustomTracePropagation()));
		Mockito.when(beanFactory.getBeanProvider(Propagation.class))
				.thenReturn(new SimpleObjectProvider(new CustomTracePropagation()));

		CompositePropagationFactorySupplier supplier = new CompositePropagationFactorySupplier(beanFactory,
				Collections.emptyList(), Collections.singletonList(PropagationType.CUSTOM));

		BDDAssertions.then(supplier.get().get().keys()).containsExactly(CustomTraceExtractor.CUSTOM_TRACE_HEADER);
	}

}

class CustomTracePropagation extends Propagation.Factory implements Propagation<String> {

	public static final List<String> KEYS = Collections.singletonList(CustomTraceExtractor.CUSTOM_TRACE_HEADER);

	@Override
	public List<String> keys() {
		return KEYS;
	}

	@Override
	public <R> TraceContext.Injector<R> injector(Setter<R, String> setter) {
		return (traceContext, request) -> {
			String trace = traceContext.traceIdString() + ":" + traceContext.spanIdString();
			setter.put(request, CustomTraceExtractor.CUSTOM_TRACE_HEADER, trace);
		};
	}

	@Override
	public <R> TraceContext.Extractor<R> extractor(Getter<R, String> getter) {
		Objects.requireNonNull(getter);
		return new CustomTraceExtractor<>(getter);
	}

	@Override
	public <K> Propagation<K> create(KeyFactory<K> keyFactory) {
		return StringPropagationAdapter.create(this, keyFactory);
	}

}

class CustomTraceExtractor<R> implements TraceContext.Extractor<R> {

	static final String CUSTOM_TRACE_HEADER = "x-custom-trace";

	final Propagation.Getter<R, String> getter;

	CustomTraceExtractor(Propagation.Getter<R, String> getter) {
		this.getter = getter;
	}

	@Override
	@SuppressWarnings("ReturnCount")
	public TraceContextOrSamplingFlags extract(R request) {
		String traceString = getter.get(request, CUSTOM_TRACE_HEADER);
		if (!StringUtils.hasText(traceString)) {
			return TraceContextOrSamplingFlags.EMPTY;
		}
		String[] trace = traceString.split(":");
		if (trace.length != 2) {
			return TraceContextOrSamplingFlags.EMPTY;
		}

		try {
			TraceContext traceContext = TraceContext.newBuilder().traceId(HexCodec.lowerHexToUnsignedLong(trace[0]))
					.spanId(HexCodec.lowerHexToUnsignedLong(trace[1])).build();

			return TraceContextOrSamplingFlags.create(traceContext);
		}
		catch (NumberFormatException ex) {
			return TraceContextOrSamplingFlags.EMPTY;
		}
	}

}
