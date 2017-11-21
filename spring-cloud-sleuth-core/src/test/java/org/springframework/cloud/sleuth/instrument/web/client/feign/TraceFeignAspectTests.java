package org.springframework.cloud.sleuth.instrument.web.client.feign;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.HashMap;

import feign.Client;
import feign.Request;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.beans.factory.BeanFactory;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * @author Marcin Grzejszczak
 */
@RunWith(MockitoJUnitRunner.class)
public class TraceFeignAspectTests {
	
	@Mock BeanFactory beanFactory;
	@Mock Client client;
	@Mock ProceedingJoinPoint pjp;
	@Mock TraceLoadBalancerFeignClient traceLoadBalancerFeignClient;
	TraceFeignAspect traceFeignAspect;
	
	@Before
	public void setup() {
		stubPjp();
		this.traceFeignAspect = new TraceFeignAspect(this.beanFactory) {
			@Override Object executeTraceFeignClient(Object bean, ProceedingJoinPoint pjp) throws IOException {
				return null;
			}
		};
	}

	private void stubPjp() {
		Request request = Request.create("foo", "bar", new HashMap<>(), new byte[] {}, Charset
				.defaultCharset());
		Request.Options options = new Request.Options();
		given(this.pjp.getArgs()).willReturn(new Object[] {request, options} );
	}

	@Test 
	public void should_wrap_feign_client_in_trace_representation() throws Throwable {
		given(this.pjp.getTarget()).willReturn(this.client);
		
		this.traceFeignAspect.feignClientWasCalled(this.pjp);

		verify(this.pjp, never()).proceed();
	}
	
	@Test 
	public void should_not_wrap_traced_feign_client_in_trace_representation() throws Throwable {
		given(this.pjp.getTarget()).willReturn(new TraceFeignClient(this.beanFactory, this.client));

		this.traceFeignAspect.feignClientWasCalled(this.pjp);

		verify(this.pjp).proceed();
	}
	
	@Test 
	public void should_not_wrap_traced_load_balancer_feign_client_in_trace_representation() throws Throwable {
		given(this.pjp.getTarget()).willReturn(this.traceLoadBalancerFeignClient);

		this.traceFeignAspect.feignClientWasCalled(this.pjp);

		verify(this.pjp).proceed();
	}

}