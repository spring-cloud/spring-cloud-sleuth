package org.springframework.cloud.sleuth.instrument.web.client.feign;

import feign.Client;
import feign.Retryer;
import feign.codec.Decoder;
import feign.codec.ErrorDecoder;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.cloud.sleuth.Tracer;

/**
 * Class that wraps Feign related classes into their Trace representative
 *
 * @author Marcin Grzejszczak
 * @since 1.0.1
 */
final class TraceFeignObjectWrapper {

	private final BeanFactory beanFactory;
	private Tracer tracer;

	TraceFeignObjectWrapper(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}

	Object wrap(Object bean) {
		if (bean instanceof Decoder && !(bean instanceof TraceFeignDecoder)) {
			return new TraceFeignDecoder(getTracer(), (Decoder) bean);
		} else if (bean instanceof Retryer && !(bean instanceof TraceFeignRetryer)) {
			return new TraceFeignRetryer(getTracer(), (Retryer) bean);
		} else if (bean instanceof Client && !(bean instanceof TraceFeignClient)) {
			return new TraceFeignClient(getTracer(), (Client) bean);
		} else if (bean instanceof ErrorDecoder && !(bean instanceof TraceFeignErrorDecoder)) {
			return new TraceFeignErrorDecoder(getTracer(), (ErrorDecoder) bean);
		}
		return bean;
	}

	private Tracer getTracer() {
		if (this.tracer==null) {
			this.tracer = this.beanFactory.getBean(Tracer.class);
		}
		return this.tracer;
	}
}
