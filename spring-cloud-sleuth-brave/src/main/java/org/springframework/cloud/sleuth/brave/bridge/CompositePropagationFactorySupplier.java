/*
 * Copyright 2013-2020 the original author or authors.
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import brave.internal.propagation.StringPropagationAdapter;
import brave.propagation.B3Propagation;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import brave.propagation.aws.AWSPropagation;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.cloud.sleuth.brave.propagation.PropagationFactorySupplier;
import org.springframework.cloud.sleuth.brave.propagation.PropagationType;

/**
 * Merges various propagation factories into a composite.
 *
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
public class CompositePropagationFactorySupplier implements PropagationFactorySupplier {

	private final BeanFactory beanFactory;

	private final List<String> localFields;

	private final List<PropagationType> types;

	public CompositePropagationFactorySupplier(BeanFactory beanFactory, List<String> localFields,
			List<PropagationType> types) {
		this.beanFactory = beanFactory;
		this.localFields = localFields;
		this.types = types;
	}

	@Override
	public Propagation.Factory get() {
		return new CompositePropagationFactory(
				this.beanFactory.getBeanProvider(BraveBaggageManager.class).getIfAvailable(BraveBaggageManager::new),
				this.localFields, this.types);
	}

}

class CompositePropagationFactory extends Propagation.Factory implements Propagation<String> {

	private final Map<PropagationType, Propagation<String>> mapping = new HashMap<>();

	private final List<PropagationType> types;

	CompositePropagationFactory(BraveBaggageManager braveBaggageManager, List<String> localFields,
			List<PropagationType> types) {
		this.types = types;
		this.mapping.put(PropagationType.AWS, AWSPropagation.FACTORY.get());
		// Note: Versions <2.2.3 use injectFormat(MULTI) for non-remote (ex
		// spring-messaging)
		// See #1643
		this.mapping.put(PropagationType.B3,
				B3Propagation.newFactoryBuilder().injectFormat(B3Propagation.Format.SINGLE_NO_PARENT).build().get());
		this.mapping.put(PropagationType.W3C, new W3CPropagation(braveBaggageManager, localFields));
		this.mapping.put(PropagationType.CUSTOM, NoOpPropagation.INSTANCE);
	}

	@Override
	public List<String> keys() {
		return this.types.stream().map(this.mapping::get).flatMap(p -> p.keys().stream()).collect(Collectors.toList());
	}

	@Override
	public <R> TraceContext.Injector<R> injector(Setter<R, String> setter) {
		return (traceContext, request) -> {
			this.types.stream().map(this.mapping::get).forEach(p -> p.injector(setter).inject(traceContext, request));
		};
	}

	@Override
	public <R> TraceContext.Extractor<R> extractor(Getter<R, String> getter) {
		return request -> {
			for (PropagationType type : this.types) {
				Propagation<String> propagator = this.mapping.get(type);
				if (propagator == null || propagator == NoOpPropagation.INSTANCE) {
					continue;
				}
				TraceContextOrSamplingFlags extract = propagator.extractor(getter).extract(request);
				if (extract != TraceContextOrSamplingFlags.EMPTY) {
					return extract;
				}
			}
			return TraceContextOrSamplingFlags.EMPTY;
		};
	}

	@Override
	public <K> Propagation<K> create(KeyFactory<K> keyFactory) {
		return StringPropagationAdapter.create(this, keyFactory);
	}

	private static class NoOpPropagation implements Propagation<String> {

		static final NoOpPropagation INSTANCE = new NoOpPropagation();

		@Override
		public List<String> keys() {
			return Collections.emptyList();
		}

		@Override
		public <R> TraceContext.Injector<R> injector(Setter<R, String> setter) {
			return (traceContext, request) -> {

			};
		}

		@Override
		public <R> TraceContext.Extractor<R> extractor(Getter<R, String> getter) {
			return request -> TraceContextOrSamplingFlags.EMPTY;
		}

	}

}
