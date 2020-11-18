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

package org.springframework.cloud.sleuth.brave.baggage;

import brave.baggage.BaggageField;
import brave.baggage.BaggagePropagationConfig;
import brave.sampler.Sampler;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jmx.JmxAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.autoconfigure.quartz.QuartzAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.sleuth.api.Span;
import org.springframework.cloud.sleuth.api.exporter.FinishedSpan;
import org.springframework.cloud.sleuth.brave.BraveTestSpanHandler;
import org.springframework.cloud.sleuth.brave.bridge.BraveAccessor;
import org.springframework.cloud.sleuth.test.TestSpanHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;

import static java.util.Arrays.asList;
import static org.assertj.core.api.BDDAssertions.then;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@ContextConfiguration(classes = MultipleHopsIntegrationTests.Config.class)
public class MultipleHopsIntegrationTests
		extends org.springframework.cloud.sleuth.baggage.multiple.MultipleHopsIntegrationTests {

	static final BaggageField REQUEST_ID = BaggageField.create("x-vcap-request-id");
	static final BaggageField COUNTRY_CODE = BaggageField.create("country-code");

	@Override
	protected void assertSpanNames() {
		then(this.spans).extracting(FinishedSpan::getName).containsAll(asList("GET /greeting", "send"));
	}

	@Override
	protected void assertBaggage(Span initialSpan) {
		// set with baggage api
		then(this.application.allSpans()).as("All have request ID")
				.allMatch(span -> "f4308d05-2228-4468-80f6-92a8377ba193"
						.equals(REQUEST_ID.getValue(BraveAccessor.traceContext(span.context()))));

		// baz is not tagged in the initial span, only downstream!
		then(this.application.allSpans()).as("All downstream have country-code")
				.filteredOn(span -> !span.equals(initialSpan))
				.allMatch(span -> "FO".equals(COUNTRY_CODE.getValue(BraveAccessor.traceContext(span.context()))));
	}

	@Configuration(proxyBeanMethods = false)
	@EnableAutoConfiguration(
			exclude = { MongoAutoConfiguration.class, QuartzAutoConfiguration.class, JmxAutoConfiguration.class })
	static class Config {

		@Bean
		TestSpanHandler testSpanHandlerSupplier(brave.test.TestSpanHandler testSpanHandler) {
			return new BraveTestSpanHandler(testSpanHandler);
		}

		@Bean
		Sampler alwaysSampler() {
			return Sampler.ALWAYS_SAMPLE;
		}

		@Bean
		brave.test.TestSpanHandler braveTestSpanHandler() {
			return new brave.test.TestSpanHandler();
		}

		@Bean
		BaggagePropagationConfig notInProperties() {
			return BaggagePropagationConfig.SingleBaggageField.remote(BaggageField.create("bar"));
		}

	}

}
