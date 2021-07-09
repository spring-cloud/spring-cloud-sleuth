/*
 * Copyright 2018-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.sleuth.instrument.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;

import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.BeanFactory;

/**
 * Factory to create a {@link TraceCqlSession}.
 *
 * @author Mark Paluch
 * @since 3.1.0
 */
public final class TraceCqlSession {

	private TraceCqlSession() {
		throw new IllegalStateException("Can't instantiate a utility class");
	}

	public static CqlSession create(CqlSession session, BeanFactory beanFactory) {
		ProxyFactory proxyFactory = new ProxyFactory();
		proxyFactory.setTarget(session);
		proxyFactory.addAdvice(new TraceCqlSessionInterceptor(session, beanFactory));
		proxyFactory.addInterface(CqlSession.class);
		return (CqlSession) proxyFactory.getProxy();
	}

}
