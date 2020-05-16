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
import com.netflix.client.ClientException;
import feign.Client;
import feign.Request;
import feign.Response;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.cloud.netflix.ribbon.SpringClientFactory;
import org.springframework.cloud.openfeign.loadbalancer.FeignBlockingLoadBalancerClient;
import org.springframework.cloud.openfeign.ribbon.CachingSpringLoadBalancerFactory;
import org.springframework.cloud.openfeign.ribbon.LoadBalancerFeignClient;

/**
 * We need to wrap the {@link LoadBalancerFeignClient} into a trace representation due to
 * casts in {@link org.springframework.cloud.openfeign.FeignClientFactoryBean}.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.7
 * @deprecated This type should have never been public and will be hidden or removed in
 * 3.0
 */
@Deprecated
public class TraceLoadBalancerFeignClient extends LoadBalancerFeignClient {

	private static final Log log = LogFactory.getLog(TraceLoadBalancerFeignClient.class);

	private final BeanFactory beanFactory;

	Tracer tracer;

	HttpTracing httpTracing;

	TracingFeignClient tracingFeignClient;

	public TraceLoadBalancerFeignClient(Client delegate,
			CachingSpringLoadBalancerFactory lbClientFactory,
			SpringClientFactory clientFactory, BeanFactory beanFactory) {
		super(delegate, lbClientFactory, clientFactory);
		this.beanFactory = beanFactory;
	}

	@Override
	public Response execute(Request request, Request.Options options) throws IOException {
		if (log.isDebugEnabled()) {
			log.debug("Before send");
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
			if (log.isDebugEnabled()) {
				log.debug("After receive");
			}
			return response;
		}
		catch (Exception e) {
			if (log.isDebugEnabled()) {
				log.debug("Exception thrown", e);
			}
			if (e instanceof IOException || e.getCause() != null
					&& e.getCause() instanceof ClientException
					&& ((ClientException) e.getCause())
							.getErrorType() == ClientException.ErrorType.GENERAL) {
				if (log.isDebugEnabled()) {
					log.debug(
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

	private boolean delegateIsALoadBalancer() {
		return getDelegate() instanceof LoadBalancerFeignClient
				|| getDelegate() instanceof FeignBlockingLoadBalancerClient;
	}

	private Tracer tracer() {
		if (this.tracer == null) {
			this.tracer = this.beanFactory.getBean(Tracer.class);
		}
		return this.tracer;
	}

	private HttpTracing httpTracing() {
		if (this.httpTracing == null) {
			this.httpTracing = this.beanFactory.getBean(HttpTracing.class);
		}
		return this.httpTracing;
	}

	private TracingFeignClient tracingFeignClient() {
		if (this.tracingFeignClient == null) {
			this.tracingFeignClient = (TracingFeignClient) TracingFeignClient
					.create(httpTracing(), getDelegate());
		}
		return this.tracingFeignClient;
	}

}
