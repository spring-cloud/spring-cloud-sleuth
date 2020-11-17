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

package org.springframework.cloud.sleuth.instrument.messaging;

import brave.Tracing;
import org.apache.kafka.streams.KafkaClientSupplier;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.StreamsBuilderFactoryBean;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author Tim te Beek
 */
@SpringBootTest(classes = SleuthKafkaStreamsAutoConfigurationTest.Config.class, webEnvironment = WebEnvironment.NONE)
public class SleuthKafkaStreamsAutoConfigurationTest {

	@Autowired
	TestTraceStreamsBuilderFactoryBean streamsBuilderFactoryBean;

	@Test
	public void clientSupplierInvokedOnStreamsBuilderFactoryBean() {
		then(streamsBuilderFactoryBean.clientSupplierInvoked).isTrue();
	}

	@Configuration(proxyBeanMethods = false)
	@EnableAutoConfiguration
	protected static class Config {

		@Bean
		Tracing tracing() {
			return Tracing.newBuilder().build();
		}

		@Bean
		StreamsBuilderFactoryBean streamsBuilderFactoryBean() {
			TestTraceStreamsBuilderFactoryBean factoryBean = new TestTraceStreamsBuilderFactoryBean();
			factoryBean.setAutoStartup(false);
			return factoryBean;
		}

	}

}

class TestTraceStreamsBuilderFactoryBean extends StreamsBuilderFactoryBean {

	boolean clientSupplierInvoked;

	@Override
	public void setClientSupplier(KafkaClientSupplier clientSupplier) {
		this.clientSupplierInvoked = true;
		super.setClientSupplier(clientSupplier);
	}

}
