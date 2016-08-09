package org.springframework.cloud.sleuth.instrument.web.client.feign;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.cloud.sleuth.Tracer;

import feign.Client;

import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * @author Marcin Grzejszczak
 */
@RunWith(MockitoJUnitRunner.class)
public class TraceFeignObjectWrapperTests {

	@Mock Tracer tracer;
	@Mock BeanFactory beanFactory;
	@InjectMocks TraceFeignObjectWrapper traceFeignObjectWrapper;

	@Before
	public void setup() {
		given(this.beanFactory.getBean(Tracer.class)).willReturn(this.tracer);
	}

	@Test
	public void should_wrap_a_client_into_trace_client() throws Exception {
		then(this.traceFeignObjectWrapper.wrap(mock(Client.class))).isExactlyInstanceOf(TraceFeignClient.class);
	}

	@Test
	public void should_not_wrap_a_bean_that_is_not_feign_related() throws Exception {
		String notFeignRelatedObject = "object";
		then(this.traceFeignObjectWrapper.wrap(notFeignRelatedObject)).isSameAs(notFeignRelatedObject);
	}
}