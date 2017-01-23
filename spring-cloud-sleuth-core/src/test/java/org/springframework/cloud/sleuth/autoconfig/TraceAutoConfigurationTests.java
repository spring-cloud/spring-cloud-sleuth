/**
 * Copyright 2015-2016 The OpenZipkin Authors
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.springframework.cloud.sleuth.autoconfig;

import org.junit.After;
import org.junit.Test;
import org.springframework.boot.autoconfigure.PropertyPlaceholderAutoConfiguration;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.log.SleuthLogAutoConfiguration;
import org.springframework.cloud.sleuth.sampler.NeverSampler;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.util.EnvironmentTestUtils.addEnvironment;

public class TraceAutoConfigurationTests {

	AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

	@After public void close() {
		context.close();
	}

	@Test public void defaultsTo64BitTraceId() {
		context = new AnnotationConfigApplicationContext();
		context.register(PropertyPlaceholderAutoConfiguration.class,
				SleuthLogAutoConfiguration.class, TraceAutoConfiguration.class);
		context.refresh();
		Tracer tracer = context.getBean(Tracer.class);

		Span span = null;
		try {
			span = tracer.createSpan("foo", NeverSampler.INSTANCE);
			assertThat(span.getTraceIdHigh()).isEqualTo(0L);
			assertThat(span.getTraceId()).isNotEqualTo(0L);
		}
		finally {
			if (span != null) {
				tracer.close(span);
			}
		}
	}

	@Test public void optInto128BitTraceId() {
		addEnvironment(context, "spring.sleuth.traceId128:true");
		context.register(PropertyPlaceholderAutoConfiguration.class,
				SleuthLogAutoConfiguration.class, TraceAutoConfiguration.class);
		context.refresh();
		Tracer tracer = context.getBean(Tracer.class);

		Span span = null;
		try {
			span = tracer.createSpan("foo", NeverSampler.INSTANCE);
			assertThat(span.getTraceIdHigh()).isNotEqualTo(0L);
			assertThat(span.getTraceId()).isNotEqualTo(0L);
		}
		finally {
			if (span != null) {
				tracer.close(span);
			}
		}
	}
}
