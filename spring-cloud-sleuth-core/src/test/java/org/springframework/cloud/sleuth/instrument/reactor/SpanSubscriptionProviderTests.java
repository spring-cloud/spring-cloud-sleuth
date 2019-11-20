/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.reactor;

import org.assertj.core.api.BDDAssertions;
import org.junit.Test;
import org.mockito.BDDMockito;
import reactor.util.context.Context;

import org.springframework.beans.factory.BeanFactory;

public class SpanSubscriptionProviderTests {

	@Test
	public void should_return_default_tracing_instance_when_exception_thrown_upon_bean_retrieval() {
		BeanFactory beanFactory = BDDMockito.mock(BeanFactory.class);
		BDDMockito.when(beanFactory.getBean(BDDMockito.any(Class.class)))
				.thenThrow(new IllegalStateException());
		SpanSubscriptionProvider provider = new SpanSubscriptionProvider(beanFactory,
				null, Context.empty(), "example");

		SpanSubscription spanSubscription = provider.get();

		BDDAssertions.then(spanSubscription).isNotNull();
	}

}
