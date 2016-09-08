package org.springframework.cloud.sleuth.instrument.web.client.feign;

import java.io.IOException;
import java.lang.invoke.MethodHandles;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.cloud.netflix.feign.ribbon.CachingSpringLoadBalancerFactory;
import org.springframework.cloud.netflix.feign.ribbon.LoadBalancerFeignClient;
import org.springframework.cloud.netflix.ribbon.SpringClientFactory;
import org.springframework.cloud.sleuth.Tracer;

import feign.Client;
import feign.Request;
import feign.Response;

/**
 * We need to wrap the {@link LoadBalancerFeignClient} into a trace representation
 * due to casts in {@link org.springframework.cloud.netflix.feign.FeignClientFactoryBean}.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.7
 */
class TraceLoadBalancerFeignClient extends LoadBalancerFeignClient {

	private static final Log log = LogFactory.getLog(MethodHandles.lookup().lookupClass());

	private final BeanFactory beanFactory;
	private Tracer tracer;

	TraceLoadBalancerFeignClient(Client delegate,
			CachingSpringLoadBalancerFactory lbClientFactory,
			SpringClientFactory clientFactory, BeanFactory beanFactory) {
		super(wrap(delegate, beanFactory), lbClientFactory, clientFactory);
		this.beanFactory = beanFactory;
	}

	@Override public Response execute(Request request, Request.Options options)
			throws IOException {
		return super.execute(request, options);
	}

	private static Client wrap(Client delegate, BeanFactory beanFactory) {
		return (Client) new TraceFeignObjectWrapper(beanFactory).wrap(delegate);
	}

	private Tracer tracer() {
		if (this.tracer == null) {
			this.tracer = this.beanFactory.getBean(Tracer.class);
		}
		return this.tracer;
	}
}
