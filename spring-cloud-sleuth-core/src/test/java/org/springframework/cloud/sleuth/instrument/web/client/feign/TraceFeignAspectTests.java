/*
 * Copyright 2013-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.sleuth.instrument.web.client.feign;

import feign.Request;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.cloud.netflix.feign.ribbon.CachingSpringLoadBalancerFactory;
import org.springframework.cloud.netflix.ribbon.SpringClientFactory;
import org.springframework.cloud.sleuth.Tracer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * @author ScienJus
 */
@RunWith(MockitoJUnitRunner.class)
public class TraceFeignAspectTests {

    @Mock Tracer tracer;
    Request request = Request.create("GET", "http://hello/world", Collections.emptyMap(), new byte[0], StandardCharsets.UTF_8);
    Request.Options options = new Request.Options();
    @Mock BeanFactory beanFactory;
    @Mock CachingSpringLoadBalancerFactory cachingSpringLoadBalancerFactory;
    @Mock SpringClientFactory springClientFactory;

    @Mock TraceLoadBalancerFeignClient traceLoadBalancerFeignClient;
    @Mock TraceFeignClient traceFeignClient;

    private TraceFeignAspect aspect;

    @Mock
    private ProceedingJoinPoint proceedingJoinPoint;

    @Before
    public void setup() throws IOException {
        given(this.proceedingJoinPoint.getArgs()).willReturn(new Object[]{request, options});
        given(this.beanFactory.getBean(Tracer.class)).willReturn(this.tracer);
        given(this.beanFactory.getBean(CachingSpringLoadBalancerFactory.class)).willReturn(this.cachingSpringLoadBalancerFactory);
        given(this.beanFactory.getBean(SpringClientFactory.class)).willReturn(this.springClientFactory);
        aspect = new TraceFeignAspect(beanFactory);
    }

    @Test
    public void traceLoadBalancerFeignClient_should_execute_without_wrapped() throws Throwable {
        given(this.proceedingJoinPoint.getTarget()).willReturn(this.traceLoadBalancerFeignClient);
        aspect.feignClientWasCalled(proceedingJoinPoint);
        verify(proceedingJoinPoint, times(1)).proceed();
    }


    @Test
    public void traceFeignClient_should_execute_without_wrapped() throws Throwable {
        given(this.proceedingJoinPoint.getTarget()).willReturn(this.traceFeignClient);
        aspect.feignClientWasCalled(proceedingJoinPoint);
        verify(proceedingJoinPoint, times(1)).proceed();
    }

}
