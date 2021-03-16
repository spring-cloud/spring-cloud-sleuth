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

package org.springframework.cloud.sleuth.instrument.web.client;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.core.Scannable;
import reactor.core.publisher.Mono;
import reactor.util.annotation.Nullable;
import reactor.util.context.Context;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.cloud.sleuth.CurrentTraceContext;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.TraceContext;
import org.springframework.cloud.sleuth.http.HttpClientHandler;
import org.springframework.cloud.sleuth.http.HttpClientRequest;
import org.springframework.cloud.sleuth.http.HttpClientResponse;
import org.springframework.cloud.sleuth.instrument.reactor.TraceContextPropagator;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * {@link BeanPostProcessor} to wrap a {@link WebClient} instance into its trace
 * representation.
 *
 * @author Marcin Grzejszczak
 * @since 2.0.0
 */
public class TraceWebClientBeanPostProcessor implements BeanPostProcessor {

	final ConfigurableApplicationContext springContext;

	public TraceWebClientBeanPostProcessor(ConfigurableApplicationContext springContext) {
		this.springContext = springContext;
	}

	@Override
	public Object postProcessBeforeInitialization(Object bean, String beanName) {
		return bean;
	}

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) {
		if (bean instanceof WebClient) {
			WebClient webClient = (WebClient) bean;
			return wrapBuilder(webClient.mutate()).build();
		}
		else if (bean instanceof WebClient.Builder) {
			WebClient.Builder webClientBuilder = (WebClient.Builder) bean;
			return wrapBuilder(webClientBuilder);
		}
		return bean;
	}

	private WebClient.Builder wrapBuilder(WebClient.Builder webClientBuilder) {
		return webClientBuilder.filters(addTraceExchangeFilterFunctionIfNotPresent());
	}

	private Consumer<List<ExchangeFilterFunction>> addTraceExchangeFilterFunctionIfNotPresent() {
		return functions -> {
			boolean noneMatch = noneMatchTraceExchangeFunction(functions);
			if (noneMatch) {
				functions.add(new TraceExchangeFilterFunction(this.springContext));
			}
		};
	}

	private boolean noneMatchTraceExchangeFunction(List<ExchangeFilterFunction> functions) {
		for (ExchangeFilterFunction function : functions) {
			if (function instanceof TraceExchangeFilterFunction) {
				return false;
			}
		}
		return true;
	}

}

