/*
 * Copyright 2013-2021 the original author or authors.
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

package org.springframework.cloud.sleuth.autoconfig.actuate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.BDDAssertions.then;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ContextConfiguration(classes = TraceSleuthActuatorAutoConfigurationIntegrationTests.TestConfig.class)
@TestPropertySource(properties = { "spring.jackson.serialization.indent_output=true",
		"management.endpoints.web.exposure.include=*", "spring.jackson.default-property-inclusion=non_null" })
public abstract class TraceSleuthActuatorAutoConfigurationIntegrationTests {

	protected MockMvc mockMvc;

	@Autowired
	WebApplicationContext applicationContext;

	@Autowired
	Tracer tracer;

	@Autowired
	BufferingSpanReporter bufferingSpanReporter;

	@BeforeEach
	void setup() throws InterruptedException {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(this.applicationContext).build();
		Span start = this.tracer.nextSpan().name("first").start();
		Thread.sleep(5);
		Span second = this.tracer.nextSpan().name("second").start();
		Thread.sleep(10);
		Span third = this.tracer.nextSpan().name("third").start();
		Thread.sleep(15);
		third.end();
		second.end();
		start.end();
	}

	@Test
	void tracesSnapshot() throws Exception {
		then(this.bufferingSpanReporter.spans).isNotEmpty();

		this.mockMvc.perform(get("/actuator/traces")).andExpect(status().isOk()).andExpect(zipkinJsonBody());

		then(this.bufferingSpanReporter.spans).isNotEmpty();
	}

	@Test
	void traces() throws Exception {
		then(this.bufferingSpanReporter.spans).isNotEmpty();

		this.mockMvc.perform(post("/actuator/traces").contentType(MediaType.APPLICATION_JSON_VALUE))
				.andExpect(status().isOk()).andExpect(zipkinJsonBody());

		then(this.bufferingSpanReporter.spans).isEmpty();
	}

	protected ResultMatcher zipkinJsonBody() {
		return content().string(allOf(containsString("\"name\":\"first\""), containsString("\"name\":\"second\""),
				containsString("\"name\":\"third\"")));
	}

	@Configuration(proxyBeanMethods = false)
	@EnableAutoConfiguration
	static class TestConfig {

	}

}
