package org.springframework.cloud.sleuth.instrument.web.client.feign;

import brave.Tracing;
import brave.http.HttpTracing;
import feign.Client;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.beans.factory.BeanFactory;

import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.Mockito.mock;

/**
 * @author Marcin Grzejszczak
 */
@RunWith(MockitoJUnitRunner.class)
public class TracingFeignObjectWrapperTests {

	Tracing tracing = Tracing.newBuilder().build();
	HttpTracing httpTracing = HttpTracing.create(this.tracing);
	@Mock BeanFactory beanFactory;
	@InjectMocks TraceFeignObjectWrapper traceFeignObjectWrapper;

	@Test
	public void should_wrap_a_client_into_lazy_trace_client() throws Exception {
		then(this.traceFeignObjectWrapper.wrap(mock(Client.class))).isExactlyInstanceOf(LazyTracingFeignClient.class);
	}

	@Test
	public void should_not_wrap_a_bean_that_is_not_feign_related() throws Exception {
		String notFeignRelatedObject = "object";
		then(this.traceFeignObjectWrapper.wrap(notFeignRelatedObject)).isSameAs(notFeignRelatedObject);
	}
}