package org.springframework.cloud.sleuth.instrument.web.client.feign;

import org.springframework.beans.factory.BeanFactory;

import feign.Client;
import feign.Retryer;
import feign.codec.Decoder;
import feign.codec.ErrorDecoder;

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
		if (bean instanceof Decoder && !(bean instanceof TraceFeignDecoder)) {
			return new TraceFeignDecoder(this.beanFactory, (Decoder) bean);
		} else if (bean instanceof Retryer && !(bean instanceof TraceFeignRetryer)) {
			return new TraceFeignRetryer(this.beanFactory, (Retryer) bean);
		} else if (bean instanceof Client && !(bean instanceof TraceFeignClient)) {
			return new TraceFeignClient(this.beanFactory, (Client) bean);
		} else if (bean instanceof ErrorDecoder && !(bean instanceof TraceFeignErrorDecoder)) {
			return new TraceFeignErrorDecoder(this.beanFactory, (ErrorDecoder) bean);
		}
		return bean;
	}
}
