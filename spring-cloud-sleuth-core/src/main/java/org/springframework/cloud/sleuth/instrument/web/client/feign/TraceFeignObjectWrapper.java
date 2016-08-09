package org.springframework.cloud.sleuth.instrument.web.client.feign;

import org.springframework.beans.factory.BeanFactory;

import feign.Client;

/**
 * Class that wraps Feign related classes into their Trace representative
 *
 * @author Marcin Grzejszczak
 * @since 1.0.1
 */
final class TraceFeignObjectWrapper {

	private final BeanFactory beanFactory;

	TraceFeignObjectWrapper(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}

	Object wrap(Object bean) {
		if (bean instanceof Client && !(bean instanceof TraceFeignClient)) {
			return new TraceFeignClient(this.beanFactory, (Client) bean);
		}
		return bean;
	}
}
