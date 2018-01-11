package org.springframework.cloud.sleuth.instrument.web.client.feign;

import java.io.IOException;

import feign.Client;
import feign.Request;
import feign.Response;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.cloud.netflix.feign.ribbon.CachingSpringLoadBalancerFactory;
import org.springframework.cloud.netflix.feign.ribbon.LoadBalancerFeignClient;
import org.springframework.cloud.netflix.ribbon.SpringClientFactory;

/**
 * We need to wrap the {@link LoadBalancerFeignClient} into a trace representation
 * due to casts in {@link org.springframework.cloud.netflix.feign.FeignClientFactoryBean}.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.7
 */
class TraceLoadBalancerFeignClient extends LoadBalancerFeignClient {

	private final BeanFactory beanFactory;

	TraceLoadBalancerFeignClient(Client delegate,
			CachingSpringLoadBalancerFactory lbClientFactory,
			SpringClientFactory clientFactory, BeanFactory beanFactory) {
		super(delegate, lbClientFactory, clientFactory);
		this.beanFactory = beanFactory;
	}

	@Override public Response execute(Request request, Request.Options options)
			throws IOException {
		return ((Client) new TraceFeignObjectWrapper(this.beanFactory).wrap(
				(Client) TraceLoadBalancerFeignClient.super::execute)).execute(request, options);
	}

}
