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

package org.springframework.cloud.sleuth.brave.instrument.web.client.integration;

import java.util.List;

import brave.Span;
import brave.internal.collect.Lists;
import brave.internal.propagation.StringPropagationAdapter;
import brave.propagation.B3Propagation;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;

public class MergedFactory extends Propagation.Factory implements Propagation<String> {

	Propagation<String> single = B3Propagation.newFactoryBuilder()
			.injectFormat(Span.Kind.CLIENT, B3Propagation.Format.SINGLE).build().get();

	Propagation<String> multi = B3Propagation.newFactoryBuilder()
			.injectFormat(Span.Kind.CLIENT, B3Propagation.Format.MULTI).build().get();

	@Override
	public List<String> keys() {
		return Lists.concat(single.keys(), multi.keys());
	}

	@Override
	public <R> TraceContext.Injector<R> injector(Setter<R, String> setter) {
		return (traceContext, request) -> {
			single.injector(setter).inject(traceContext, request);
			multi.injector(setter).inject(traceContext, request);
		};
	}

	@Override
	public <R> TraceContext.Extractor<R> extractor(Getter<R, String> getter) {
		return multi.extractor(getter);
	}

	@Override
	public <K> Propagation<K> create(KeyFactory<K> keyFactory) {
		return StringPropagationAdapter.create(this, keyFactory);
	}

}
