package org.springframework.cloud.sleuth.instrument.web.client.feign;

import java.io.IOException;

import brave.http.HttpTracing;
import feign.Client;
import feign.Request;
import feign.Response;
import org.springframework.beans.factory.BeanFactory;

/**
 * Lazilly resolves the Trace Feign Client
 *
 * @author Marcin Grzejszczak
 * @since 2.0.0
 */
class LazyTracingFeignClient implements Client {

	private Client tracingFeignClient;
	private HttpTracing httpTracing;
	private final BeanFactory beanFactory;
	private final Client delegate;

	LazyTracingFeignClient(BeanFactory beanFactory, Client delegate) {
		this.beanFactory = beanFactory;
		this.delegate = delegate;
	}

	@Override public Response execute(Request request, Request.Options options)
			throws IOException {
		return tracingFeignClient().execute(request, options);
	}

	private Client tracingFeignClient() {
		if (this.tracingFeignClient == null) {
			this.tracingFeignClient = TracingFeignClient.create(httpTracing(), this.delegate);
		}
		return this.tracingFeignClient;
	}

	private HttpTracing httpTracing() {
		if (this.httpTracing == null) {
			this.httpTracing = this.beanFactory.getBean(HttpTracing.class);
		}
		return this.httpTracing;
	}
}
