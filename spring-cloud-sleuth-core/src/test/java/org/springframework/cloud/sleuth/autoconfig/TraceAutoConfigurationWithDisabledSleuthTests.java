/*
 * Copyright 2013-2016 the original author or authors.
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
package org.springframework.cloud.sleuth.autoconfig;

import java.security.SecureRandom;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = TraceAutoConfigurationWithDisabledSleuthTests.Config.class)
@TestPropertySource(properties = "spring.sleuth.enabled=false")
public class TraceAutoConfigurationWithDisabledSleuthTests {

	@Test
	public void shouldStartContext(){

	}

	@EnableAutoConfiguration
	static class Config {
		@Bean
		public FactoryBean<SecureRandom> secureRandom() {
			return new FactoryBean<SecureRandom>() {

				@Override public SecureRandom getObject() throws Exception {
					return new SecureRandom();
				}

				@Override public Class<?> getObjectType() {
					return SecureRandom.class;
				}

				@Override public boolean isSingleton() {
					return true;
				}
			};
		}
	}
}
