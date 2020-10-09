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

package org.springframework.cloud.sleuth.instrument.web;

import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;

/**
 * @author Marcin Grzejszczak
 */
@SpringBootTest(classes = { TraceWebDisabledTests.Config.class }, properties = { "spring.sleuth.web.enabled=false" })
public class TraceWebDisabledTests {

	@Test
	public void should_load_context() {

	}

	@Configuration(proxyBeanMethods = false)
	@EnableAutoConfiguration
	public static class Config {

	}

}
