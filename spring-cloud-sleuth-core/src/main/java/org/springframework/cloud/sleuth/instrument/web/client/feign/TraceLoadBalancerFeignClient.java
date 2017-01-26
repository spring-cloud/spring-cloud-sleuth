package org.springframework.cloud.sleuth.instrument.web.client.feign;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.cloud.netflix.feign.ribbon.CachingSpringLoadBalancerFactory;
import org.springframework.cloud.netflix.feign.ribbon.LoadBalancerFeignClient;
import org.springframework.cloud.netflix.ribbon.SpringClientFactory;

import feign.Client;

/**
 * We need to wrap the {@link LoadBalancerFeignClient} into a trace representation
 * due to casts in {@link org.springframework.cloud.netflix.feign.FeignClientFactoryBean}.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.7
 */
class TraceLoadBalancerFeignClient extends LoadBalancerFeignClient {

	TraceLoadBalancerFeignClient(Client delegate,
			CachingSpringLoadBalancerFactory lbClientFactory,
			SpringClientFactory clientFactory, BeanFactory beanFactory) {
		super(wrap(delegate, beanFactory), lbClientFactory, clientFactory);
	}

	private static Client wrap(Client delegate, BeanFactory beanFactory) {
		return (Client) new TraceFeignObjectWrapper(beanFactory).wrap(delegate);
	}
}
