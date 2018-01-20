package org.springframework.cloud.sleuth.instrument.web.client.feign;

import java.io.IOException;

import brave.Tracing;
import brave.http.HttpTracing;
import brave.propagation.CurrentTraceContext;
import feign.Client;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.cloud.sleuth.TraceKeys;
import org.springframework.cloud.sleuth.instrument.web.SleuthHttpParserAccessor;

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
	Tracing tracing = Tracing.newBuilder()
			.currentTraceContext(CurrentTraceContext.Default.create())
			.build();
	TraceKeys traceKeys = new TraceKeys();
	HttpTracing httpTracing = HttpTracing.newBuilder(this.tracing)
			.clientParser(SleuthHttpParserAccessor.getClient(this.traceKeys))
			.build();
	TraceFeignAspect traceFeignAspect;
	
	@Before
	public void setup() {
		this.traceFeignAspect = new TraceFeignAspect(this.beanFactory) {
			@Override Object executeTraceFeignClient(Object bean, ProceedingJoinPoint pjp) throws IOException {
				return null;
			}
		};
	}

	@Test 
	public void should_wrap_feign_client_in_trace_representation() throws Throwable {
		given(this.pjp.getTarget()).willReturn(this.client);

		this.traceFeignAspect.feignClientWasCalled(this.pjp);

		verify(this.pjp, never()).proceed();
	}
	
	@Test 
	public void should_not_wrap_traced_feign_client_in_trace_representation() throws Throwable {
		given(this.pjp.getTarget()).willReturn(new TracingFeignClient(this.httpTracing, this.client));

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