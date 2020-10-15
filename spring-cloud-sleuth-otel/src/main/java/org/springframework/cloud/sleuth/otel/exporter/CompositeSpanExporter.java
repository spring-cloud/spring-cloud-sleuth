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

package org.springframework.cloud.sleuth.otel.exporter;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.SpanData;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.cloud.sleuth.api.exporter.SpanExporter;
import org.springframework.cloud.sleuth.otel.bridge.OtelReportedSpan;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.ResolvableType;

class CompositeSpanExporter implements io.opentelemetry.sdk.trace.export.SpanExporter {

	private List<SpanExporter> exporters;

	private final io.opentelemetry.sdk.trace.export.SpanExporter delegate;

	private final BeanFactory beanFactory;

	CompositeSpanExporter(io.opentelemetry.sdk.trace.export.SpanExporter delegate, BeanFactory beanFactory) {
		this.delegate = delegate;
		this.beanFactory = beanFactory;
	}

	@SuppressWarnings("unchecked")
	private List<SpanExporter> exporters() {
		if (this.exporters == null) {
			this.exporters = (List<SpanExporter>) this.beanFactory
					.getBeanProvider(ResolvableType.forType(new ParameterizedTypeReference<List<SpanExporter>>() {
					})).getIfAvailable(Collections::emptyList);
		}
		return this.exporters;
	}

	@Override
	public CompletableResultCode export(Collection<SpanData> spans) {
		return this.delegate.export(spans.stream().filter(this::shouldProcess).collect(Collectors.toList()));
	}

	private boolean shouldProcess(SpanData span) {
		for (SpanExporter exporter : exporters()) {
			if (!exporter.export(OtelReportedSpan.fromOtel(span))) {
				return false;
			}
		}
		return true;
	}

	@Override
	public CompletableResultCode flush() {
		return this.delegate.flush();
	}

	@Override
	public CompletableResultCode shutdown() {
		return this.delegate.shutdown();
	}

}

class CompositeSpanExporterBeanPostProcessor implements BeanPostProcessor {

	private final BeanFactory beanFactory;

	CompositeSpanExporterBeanPostProcessor(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
		if (bean instanceof io.opentelemetry.sdk.trace.export.SpanExporter) {
			return new CompositeSpanExporter((io.opentelemetry.sdk.trace.export.SpanExporter) bean, this.beanFactory);
		}
		return bean;
	}

}