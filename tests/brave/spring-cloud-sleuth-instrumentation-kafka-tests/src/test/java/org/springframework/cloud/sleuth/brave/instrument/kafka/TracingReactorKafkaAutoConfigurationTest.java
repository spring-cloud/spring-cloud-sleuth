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

package org.springframework.cloud.sleuth.brave.instrument.kafka;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.sleuth.instrument.kafka.TracingKafkaProducerFactory;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.BDDAssertions.then;

@SpringBootTest(classes = TracingReactorKafkaAutoConfigurationTest.Config.class,
		webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class TracingReactorKafkaAutoConfigurationTest {

	@Autowired
	TracingKafkaProducerFactory kafkaProducerFactory;

	@Test
	public void should_register_producer_factory() {
		then(this.kafkaProducerFactory).isNotNull();
	}

	@Configuration(proxyBeanMethods = false)
	@EnableAutoConfiguration
	protected static class Config {

	}

}
