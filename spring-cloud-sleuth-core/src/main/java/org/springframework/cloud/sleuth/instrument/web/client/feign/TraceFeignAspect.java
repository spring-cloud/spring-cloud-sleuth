/*
 * Copyright 2013-2016 the original author or authors.
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

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.BeanFactory;

import feign.Client;
import feign.Request;

/**
 * Aspect for Feign clients so that you can autowire your custom components
 *
 * @author Marcin Grzejszczak
 * @since 1.1.2
 */
@Aspect
class TraceFeignAspect {

	private final BeanFactory beanFactory;

	TraceFeignAspect(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}

	@Around("execution (* feign.Client.*(..))")
	public Object feignClientWasCalled(final ProceedingJoinPoint pjp) throws Throwable {
		Object[] args = pjp.getArgs();
		Request request = (Request) args[0];
		Request.Options options = (Request.Options) args[1];
		Object bean = pjp.getTarget();
		if (!(bean instanceof TraceFeignClient)) {
			return new TraceFeignClient(this.beanFactory, (Client) bean).execute(request, options);
		}
		return pjp.proceed();
	}
}
