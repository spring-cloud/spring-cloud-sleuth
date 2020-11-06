/*
 * Copyright 2013-2020 the original author or authors.
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

package org.springframework.cloud.sleuth.annotation;

import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.cloud.sleuth.test.TestSpanHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;

@ContextConfiguration(classes = SleuthSpanCreatorAspectNegativeTests.TestConfiguration.class)
public abstract class SleuthSpanCreatorAspectNegativeTests {

	@Autowired
	NotAnnotatedTestBeanInterface testBean;

	@Autowired
	TestBeanInterface annotatedTestBean;

	@Autowired
	TestSpanHandler spans;

	@BeforeEach
	public void setup() {
		this.spans.clear();
	}

	@Test
	public void shouldNotCallAdviceForNotAnnotatedBean() {
		this.testBean.testMethod();

		BDDAssertions.then(this.spans).isEmpty();
	}

	@Test
	public void shouldCallAdviceForAnnotatedBean() throws Throwable {
		this.annotatedTestBean.testMethod();

		BDDAssertions.then(this.spans).hasSize(1);
		BDDAssertions.then(this.spans.get(0).getName()).isEqualTo("test-method");
	}

	protected interface NotAnnotatedTestBeanInterface {

		void testMethod();

	}

	protected interface TestBeanInterface {

		@NewSpan
		void testMethod();

		void testMethod2();

		void testMethod3();

		@NewSpan(name = "testMethod4")
		void testMethod4();

		@NewSpan(name = "testMethod5")
		void testMethod5(@SpanTag("testTag") String test);

		void testMethod6(String test);

		void testMethod7();

	}

	protected static class NotAnnotatedTestBean implements NotAnnotatedTestBeanInterface {

		@Override
		public void testMethod() {
		}

	}

	protected static class TestBean implements TestBeanInterface {

		@Override
		public void testMethod() {
		}

		@NewSpan
		@Override
		public void testMethod2() {
		}

		@NewSpan(name = "testMethod3")
		@Override
		public void testMethod3() {
		}

		@Override
		public void testMethod4() {
		}

		@Override
		public void testMethod5(String test) {
		}

		@NewSpan(name = "testMethod6")
		@Override
		public void testMethod6(@SpanTag("testTag6") String test) {

		}

		@Override
		public void testMethod7() {
		}

	}

	@Configuration(proxyBeanMethods = false)
	@EnableAutoConfiguration
	public static class TestConfiguration {

		@Bean
		NotAnnotatedTestBeanInterface testBean() {
			return new NotAnnotatedTestBean();
		}

		@Bean
		TestBeanInterface annotatedTestBean() {
			return new TestBean();
		}

	}

}
