/*
 * Copyright 2013-2019 the original author or authors.
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

import brave.Span;
import brave.Tracer;
import brave.http.HttpTracing;
import feign.Client;
import feign.Request;
import feign.Response;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.cloud.loadbalancer.blocking.client.BlockingLoadBalancerClient;
import org.springframework.cloud.openfeign.loadbalancer.FeignBlockingLoadBalancerClient;

/**
 * A trace representation of {@link FeignBlockingLoadBalancerClient}. Needed due to casts
 * in {@link org.springframework.cloud.openfeign.FeignClientFactoryBean}.
 *
 * @author Olga Maciaszek-Sharma
 * @since 2.2.0
 * @see FeignBlockingLoadBalancerClient
 */
public class TraceFeignBlockingLoadBalancerClient
		extends FeignBlockingLoadBalancerClient {

	private static final Log LOG = LogFactory
			.getLog(TraceFeignBlockingLoadBalancerClient.class);

	private final BeanFactory beanFactory;

	Tracer tracer;

	HttpTracing httpTracing;

	TracingFeignClient tracingFeignClient;

	TraceFeignBlockingLoadBalancerClient(Client delegate,
			BlockingLoadBalancerClient loadBalancerClient, BeanFactory beanFactory) {
		super(delegate, loadBalancerClient);
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
			response = super.execute(request, options);
			if (LOG.isDebugEnabled()) {
				LOG.debug("After receive");
			}
			return response;
		}
		catch (Exception e) {
			if (LOG.isDebugEnabled()) {
				LOG.debug("Exception thrown", e);
			}
			if (e instanceof IOException/*
										 * || e.getCause() != null && e.getCause()
										 * instanceof ClientException &&
										 * ((ClientException) e.getCause())
										 * .getErrorType() ==
										 * ClientException.ErrorType.GENERAL
										 */) {
				if (LOG.isDebugEnabled()) {
					LOG.debug(
							"General exception was thrown, so most likely the traced client wasn't called. Falling back to a manual span");
				}
				tracingFeignClient().handleSendAndReceive(fallbackSpan, request, response,
						e);
			}
			throw e;
		}
		finally {
			fallbackSpan.abandon();
		}
	}

	private Tracer tracer() {
		if (tracer == null) {
			tracer = beanFactory.getBean(Tracer.class);
		}
		return tracer;
	}

	private HttpTracing httpTracing() {
		if (httpTracing == null) {
			httpTracing = beanFactory.getBean(HttpTracing.class);
		}
		return httpTracing;
	}

	private TracingFeignClient tracingFeignClient() {
		if (tracingFeignClient == null) {
			tracingFeignClient = (TracingFeignClient) TracingFeignClient
					.create(httpTracing(), getDelegate());
		}
		return tracingFeignClient;
	}

}
