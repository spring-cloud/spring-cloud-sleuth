/*
 * Copyright 2013-2018 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.messaging;

import brave.Tracing;
import org.junit.After;
import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.support.ChannelInterceptor;

public class TracingChannelInterceptorAutowireTest {

	@Configuration static class TracingConfiguration {
		@Bean Tracing tracing() {
			return Tracing.newBuilder().build();
		}
	}

	@Test public void autowiredWithBeanConfig() {
		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
		ctx.register(TracingConfiguration.class);
		ctx.register(TracingChannelInterceptor.class);
		ctx.refresh();

		ctx.getBean(ChannelInterceptor.class);
	}

	@After public void close() {
		Tracing.current().close();
	}
}
