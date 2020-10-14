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

package org.springframework.cloud.sleuth.baggage;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.cloud.sleuth.api.Baggage;
import org.springframework.cloud.sleuth.api.ScopedSpan;
import org.springframework.cloud.sleuth.api.Tracer;
import org.springframework.cloud.sleuth.test.TestSpanHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/**
 * @author Taras Danylchuk
 */
@ContextConfiguration(classes = BaggageTagSpanHandlerTest.TestConfig.class)
@ActiveProfiles("baggage") // application-baggage.yml
public abstract class BaggageTagSpanHandlerTest {

	Baggage countryCode;

	Baggage requestId;

	@Autowired
	private Tracer tracer;

	@Autowired
	private TestSpanHandler spans;

	private ScopedSpan span;

	@BeforeEach
	public void setUp() {
		this.spans.clear();
		this.span = this.tracer.startScopedSpan("my-scoped-span");
		this.countryCode = this.tracer.createBaggage("country-code");
		this.countryCode.updateValue("FO");
		this.requestId = this.tracer.createBaggage("x-vcap-request-id");
		this.requestId.updateValue("f4308d05-2228-4468-80f6-92a8377ba193");
	}

	@Test
	public void shouldReportWithBaggageInTags() {
		this.span.finish();

		Assertions.assertThat(this.spans).hasSize(1);
		Assertions.assertThat(this.spans.get(0).tags()).hasSize(1) // REQUEST_ID is not in
																	// the
				// tag-fields
				.containsEntry(countryCode.name(), "FO");
	}

	@EnableAutoConfiguration
	@Configuration(proxyBeanMethods = false)
	static class TestConfig {

	}

}
