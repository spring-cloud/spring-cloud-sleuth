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

import brave.Tracer;
import brave.handler.SpanHandler;
import brave.kafka.clients.KafkaTracing;
import brave.sampler.Sampler;
import brave.test.TestSpanHandler;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.KafkaListenerContainerFactory;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author Roberto Tassi
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = BraveMessagingAutoConfiguration1664Tests.Config.class,
		webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class BraveMessagingAutoConfiguration1664Tests {

	@Autowired
	MySleuthKafka1664Aspect mySleuthKafka1664Aspect;

	@Autowired
	KafkaListenerContainerFactory<?> kafkaListenerContainerFactory;

	@Test
	public void should_wrap_kafka() {
		kafkaListenerContainerFactory.createContainer("backend");
		then(this.mySleuthKafka1664Aspect.adapterWrapped).isTrue();
	}

	@Configuration(proxyBeanMethods = false)
	@EnableAutoConfiguration
	protected static class Config {

		@Bean
		Sampler sampler() {
			return Sampler.ALWAYS_SAMPLE;
		}

		@Bean
		SpanHandler testSpanHandler() {
			return new TestSpanHandler();
		}

		@Bean
		SleuthKafkaAspect sleuthKafkaAspect(KafkaTracing kafkaTracing, Tracer tracer) {
			return new MySleuthKafka1664Aspect(kafkaTracing, tracer);
		}

	}

}

class MySleuthKafka1664Aspect extends SleuthKafkaAspect {

	boolean adapterWrapped;

	MySleuthKafka1664Aspect(KafkaTracing kafkaTracing, Tracer tracer) {
		super(kafkaTracing, tracer);
	}

	@Override
	public Object wrapListenerContainerCreation(ProceedingJoinPoint pjp) throws Throwable {
		this.adapterWrapped = true;
		return Mockito.mock(MessageListenerContainer.class);
	}

}
