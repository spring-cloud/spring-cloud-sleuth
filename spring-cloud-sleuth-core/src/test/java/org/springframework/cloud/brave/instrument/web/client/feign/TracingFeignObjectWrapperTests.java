package org.springframework.cloud.brave.instrument.web.client.feign;

import brave.Tracing;
import brave.http.HttpTracing;
import feign.Client;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.BDDMockito;
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

	@Before
	public void setup() {
		BDDMockito.given(this.beanFactory.getBean(HttpTracing.class))
				.willReturn(this.httpTracing);
	}

	@Test
	public void should_wrap_a_client_into_trace_client() throws Exception {
		then(this.traceFeignObjectWrapper.wrap(mock(Client.class))).isExactlyInstanceOf(TracingFeignClient.class);
	}

	@Test
	public void should_not_wrap_a_bean_that_is_not_feign_related() throws Exception {
		String notFeignRelatedObject = "object";
		then(this.traceFeignObjectWrapper.wrap(notFeignRelatedObject)).isSameAs(notFeignRelatedObject);
	}
}