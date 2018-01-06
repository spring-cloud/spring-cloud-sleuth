package org.springframework.cloud.brave.instrument.web.client.feign;

import brave.http.HttpTracing;
import feign.Client;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.cloud.netflix.feign.ribbon.CachingSpringLoadBalancerFactory;
import org.springframework.cloud.netflix.feign.ribbon.LoadBalancerFeignClient;
import org.springframework.cloud.netflix.ribbon.SpringClientFactory;

/**
 * Class that wraps Feign related classes into their Trace representative
 *
 * @author Marcin Grzejszczak
 * @since 1.0.1
 */
final class TraceFeignObjectWrapper {

	private final BeanFactory beanFactory;

	private CachingSpringLoadBalancerFactory cachingSpringLoadBalancerFactory;
	private SpringClientFactory springClientFactory;
	private HttpTracing httpTracing;

	TraceFeignObjectWrapper(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}

	Object wrap(Object bean) {
		if (bean instanceof Client && !(bean instanceof TracingFeignClient)) {
			if (bean instanceof LoadBalancerFeignClient && !(bean instanceof TraceLoadBalancerFeignClient)) {
				LoadBalancerFeignClient client = ((LoadBalancerFeignClient) bean);
				return new TraceLoadBalancerFeignClient(
						(Client) new TraceFeignObjectWrapper(beanFactory).wrap(client.getDelegate()),
						factory(), clientFactory(), this.beanFactory);
			} else if (bean instanceof TraceLoadBalancerFeignClient) {
				return bean;
			}
			return TracingFeignClient.create(httpTracing(), (Client) bean);
		}
		return bean;
	}

	private CachingSpringLoadBalancerFactory factory() {
		if (this.cachingSpringLoadBalancerFactory == null) {
			this.cachingSpringLoadBalancerFactory = this.beanFactory
					.getBean(CachingSpringLoadBalancerFactory.class);
		}
		return this.cachingSpringLoadBalancerFactory;
	}

	private SpringClientFactory clientFactory() {
		if (this.springClientFactory == null) {
			this.springClientFactory = this.beanFactory
					.getBean(SpringClientFactory.class);
		}
		return this.springClientFactory;
	}

	private HttpTracing httpTracing() {
		if (this.httpTracing == null) {
			this.httpTracing = this.beanFactory.getBean(HttpTracing.class);
		}
		return this.httpTracing;
	}
}
