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

package org.springframework.cloud.sleuth.brave.propagation;

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
import org.springframework.cloud.sleuth.autoconfig.SleuthBaggageProperties;
import org.springframework.cloud.sleuth.brave.bridge.BraveBaggageManager;

class CompositePropagationFactorySupplier implements PropagationFactorySupplier {

	private final BeanFactory beanFactory;

	private final SleuthBaggageProperties baggageProperties;

	private final SleuthPropagationProperties properties;

	CompositePropagationFactorySupplier(BeanFactory beanFactory, SleuthBaggageProperties baggageProperties,
			SleuthPropagationProperties properties) {
		this.beanFactory = beanFactory;
		this.baggageProperties = baggageProperties;
		this.properties = properties;
	}

	@Override
	public Propagation.Factory get() {
		return new CompositePropagationFactory(
				this.beanFactory.getBeanProvider(BraveBaggageManager.class).getIfAvailable(BraveBaggageManager::new),
				this.baggageProperties, this.properties);
	}

}

class CompositePropagationFactory extends Propagation.Factory implements Propagation<String> {

	private final Map<SleuthPropagationProperties.PropagationType, Propagation<String>> mapping = new HashMap<>();

	private final SleuthPropagationProperties properties;

	CompositePropagationFactory(BraveBaggageManager braveBaggageManager, SleuthBaggageProperties baggageProperties,
			SleuthPropagationProperties properties) {
		this.properties = properties;
		this.mapping.put(SleuthPropagationProperties.PropagationType.AWS, AWSPropagation.FACTORY.get());
		// Note: Versions <2.2.3 use injectFormat(MULTI) for non-remote (ex
		// spring-messaging)
		// See #1643
		this.mapping.put(SleuthPropagationProperties.PropagationType.B3,
				B3Propagation.newFactoryBuilder().injectFormat(B3Propagation.Format.SINGLE_NO_PARENT).build().get());
		this.mapping.put(SleuthPropagationProperties.PropagationType.W3C,
				new W3CPropagation(braveBaggageManager, baggageProperties));
		this.mapping.put(SleuthPropagationProperties.PropagationType.CUSTOM, NoOpPropagation.INSTANCE);
	}

	@Override
	public List<String> keys() {
		return this.properties.getType().stream().map(this.mapping::get).flatMap(p -> p.keys().stream())
				.collect(Collectors.toList());
	}

	@Override
	public <R> TraceContext.Injector<R> injector(Setter<R, String> setter) {
		return (traceContext, request) -> {
			this.properties.getType().stream().map(this.mapping::get)
					.forEach(p -> p.injector(setter).inject(traceContext, request));
		};
	}

	@Override
	public <R> TraceContext.Extractor<R> extractor(Getter<R, String> getter) {
		return request -> {
			for (SleuthPropagationProperties.PropagationType type : this.properties.getType()) {
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
