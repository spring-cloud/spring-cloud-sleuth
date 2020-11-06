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

package org.springframework.cloud.sleuth.otel.baggage;

import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

import io.opentelemetry.sdk.trace.samplers.Sampler;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.sleuth.api.Span;
import org.springframework.cloud.sleuth.api.exporter.FinishedSpan;
import org.springframework.cloud.sleuth.baggage.multiple.DemoApplication;
import org.springframework.cloud.sleuth.otel.OtelTestSpanHandler;
import org.springframework.cloud.sleuth.otel.bridge.OtelBaggageInScope;
import org.springframework.cloud.sleuth.otel.exporter.ArrayListSpanProcessor;
import org.springframework.context.ApplicationListener;
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

	@Autowired
	MyBaggageChangedListener myBaggageChangedListener;

	@Autowired
	DemoApplication demoApplication;

	// TODO: Why do we have empty names here
	@Override
	protected void assertSpanNames() {
		then(this.spans).extracting(FinishedSpan::getName).containsAll(asList("HTTP GET", "handle", "send"));
	}

	@Override
	protected void assertBaggage(Span initialSpan) {
		then(this.myBaggageChangedListener.baggageChanged).as("All have request ID")
				.filteredOn(b -> b.name.equals(REQUEST_ID))
				.allMatch(event -> "f4308d05-2228-4468-80f6-92a8377ba193".equals(event.value));
		then(this.demoApplication.getBaggageValue()).isEqualTo("FO");
	}

	@Configuration(proxyBeanMethods = false)
	static class Config {

		@Bean
		OtelTestSpanHandler testSpanHandlerSupplier() {
			return new OtelTestSpanHandler(new ArrayListSpanProcessor());
		}

		@Bean
		Sampler alwaysSampler() {
			return Sampler.alwaysOn();
		}

		@Bean
		MyBaggageChangedListener myBaggageChangedListener() {
			return new MyBaggageChangedListener();
		}

	}

}

class MyBaggageChangedListener implements ApplicationListener<OtelBaggageInScope.BaggageChanged> {

	Queue<OtelBaggageInScope.BaggageChanged> baggageChanged = new LinkedBlockingQueue<>();

	@Override
	public void onApplicationEvent(OtelBaggageInScope.BaggageChanged event) {
		this.baggageChanged.add(event);
	}

}
