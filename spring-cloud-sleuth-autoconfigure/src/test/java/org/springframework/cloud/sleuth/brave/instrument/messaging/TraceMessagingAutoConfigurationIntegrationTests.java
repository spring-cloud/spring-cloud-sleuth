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

package org.springframework.cloud.sleuth.brave.instrument.messaging;

import brave.handler.SpanHandler;
import brave.messaging.MessagingRequest;
import brave.messaging.MessagingRuleSampler;
import brave.sampler.Matchers;
import brave.sampler.RateLimitingSampler;
import brave.sampler.Sampler;
import brave.sampler.SamplerFunction;
import brave.test.TestSpanHandler;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static brave.messaging.MessagingRequestMatchers.channelNameEquals;
import static org.assertj.core.api.BDDAssertions.then;

@SpringBootTest(classes = TraceMessagingAutoConfigurationIntegrationTests.Config.class,
		webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = "spring.sleuth.tracer.mode=BRAVE")
public class TraceMessagingAutoConfigurationIntegrationTests {

	@Autowired
	@ConsumerSampler
	SamplerFunction<MessagingRequest> sampler;

	@Test
	public void should_inject_messaging_sampler() {
		then(this.sampler).isNotNull();
	}

	@EnableAutoConfiguration
	@Configuration(proxyBeanMethods = false)
	public static class Config {

		@Bean
		SpanHandler testSpanHandler() {
			return new TestSpanHandler();
		}

		// tag::custom_messaging_consumer_sampler[]
		@Bean(name = ConsumerSampler.NAME)
		SamplerFunction<MessagingRequest> myMessagingSampler() {
			return MessagingRuleSampler.newBuilder().putRule(channelNameEquals("alerts"), Sampler.NEVER_SAMPLE)
					.putRule(Matchers.alwaysMatch(), RateLimitingSampler.create(100)).build();
		}
		// end::custom_messaging_consumer_sampler[]

	}

}
