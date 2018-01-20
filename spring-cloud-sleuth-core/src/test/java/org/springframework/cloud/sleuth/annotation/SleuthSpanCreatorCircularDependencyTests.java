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
package org.springframework.cloud.sleuth.annotation;

import zipkin2.Span;
import zipkin2.reporter.Reporter;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.sleuth.util.ArrayListSpanReporter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit4.SpringRunner;

@SpringBootTest(classes = SleuthSpanCreatorCircularDependencyTests.TestConfiguration.class)
@RunWith(SpringRunner.class)
public class SleuthSpanCreatorCircularDependencyTests {
	@Test public void contextLoads() throws Exception {
	}

	private static class Service1 {
		@Autowired private Service2 service2;

		@NewSpan public void foo() {
		}
	}

	private static class Service2 {
		@Autowired private Service1 service1;

		@NewSpan public void bar() {
		}
	}

	@Configuration @EnableAutoConfiguration
	protected static class TestConfiguration {
		@Bean Reporter<Span> spanReporter() {
			return new ArrayListSpanReporter();
		}

		@Bean public Service1 service1() {
			return new Service1();
		}

		@Bean public Service2 service2() {
			return new Service2();
		}
	}
}