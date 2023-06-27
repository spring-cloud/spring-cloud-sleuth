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

package org.springframework.cloud.sleuth.instrument.web.client.feign;

import java.io.IOException;

import feign.Client;
import feign.Request;
import feign.Response;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.cloud.client.loadbalancer.LoadBalancedRetryFactory;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClientsProperties;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.cloud.openfeign.loadbalancer.RetryableFeignBlockingLoadBalancerClient;
import org.springframework.cloud.sleuth.CurrentTraceContext;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.http.HttpClientHandler;

/**
 * A trace representation of {@link RetryableFeignBlockingLoadBalancerClient}. Needed due
 * to casts in {@link org.springframework.cloud.openfeign.FeignClientFactoryBean}.
 *
 * @author Olga Maciaszek-Sharma
 * @since 2.2.0
 * @see RetryableFeignBlockingLoadBalancerClient
 */
class TraceRetryableFeignBlockingLoadBalancerClient extends RetryableFeignBlockingLoadBalancerClient {

	private static final Log LOG = LogFactory.getLog(TraceRetryableFeignBlockingLoadBalancerClient.class);

	private final BeanFactory beanFactory;

	Tracer tracer;

	CurrentTraceContext currentTraceContext;

	HttpClientHandler httpClientHandler;

	TracingFeignClient tracingFeignClient;

	TraceRetryableFeignBlockingLoadBalancerClient(Client delegate, LoadBalancerClient loadBalancerClient,
			LoadBalancedRetryFactory retryFactory, LoadBalancerClientsProperties properties,
			LoadBalancerClientFactory loadBalancerClientFactory, BeanFactory beanFactory) {
		super(delegate, loadBalancerClient, retryFactory, properties, loadBalancerClientFactory);
		this.beanFactory = beanFactory;
	}

	@Override
	public Response execute(Request request, Request.Options options) throws IOException {
		if (LOG.isDebugEnabled()) {
			LOG.debug("Before send");
		}
		Response response = null;
		Span fallbackSpan = tracer().nextSpan().start();
		try {
			if (delegateIsALoadBalancer()) {
				response = getDelegate().execute(request, options);
			}
			else {
				response = super.execute(request, options);
			}
			if (LOG.isDebugEnabled()) {
				LOG.debug("After receive");
			}
			return response;
		}
		catch (Exception e) {
			if (LOG.isDebugEnabled()) {
				LOG.debug("Exception thrown", e);
			}
			if (e instanceof IOException) {
				if (LOG.isDebugEnabled()) {
					LOG.debug(
							"IOException was thrown, so most likely the traced client wasn't called. Falling back to a manual span.");
				}
				tracingFeignClient().handleSendAndReceive(fallbackSpan, request, response, e);
			}
			throw e;
		}
		finally {
			fallbackSpan.abandon();
		}
	}

	private boolean delegateIsALoadBalancer() {
		return getDelegate() instanceof RetryableFeignBlockingLoadBalancerClient;
	}

	private Tracer tracer() {
		if (tracer == null) {
			tracer = beanFactory.getBean(Tracer.class);
		}
		return tracer;
	}

	private CurrentTraceContext currentTraceContext() {
		if (currentTraceContext == null) {
			currentTraceContext = beanFactory.getBean(CurrentTraceContext.class);
		}
		return currentTraceContext;
	}

	private HttpClientHandler httpClientHandler() {
		if (httpClientHandler == null) {
			httpClientHandler = beanFactory.getBean(HttpClientHandler.class);
		}
		return httpClientHandler;
	}

	private TracingFeignClient tracingFeignClient() {
		if (tracingFeignClient == null) {
			tracingFeignClient = (TracingFeignClient) TracingFeignClient.create(currentTraceContext(),
					httpClientHandler(), getDelegate());
		}
		return tracingFeignClient;
	}

}
