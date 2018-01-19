/*
 * Copyright 2013-2018 the original author or authors.
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

import brave.Tracing;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.assertj.core.api.BDDAssertions;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.rule.OutputCapture;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = TraceAutoConfigurationWithDisabledSleuthTests.Config.class,
				properties = "spring.sleuth.enabled=false",
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("disabled")
public class TraceAutoConfigurationWithDisabledSleuthTests {

	private static final Log log = LogFactory.getLog(
			TraceAutoConfigurationWithDisabledSleuthTests.class);

	@Rule public OutputCapture capture = new OutputCapture();
	@Autowired(required = false) Tracing tracing;

	@Test
	public void shouldStartContext() {
		BDDAssertions.then(this.tracing).isNull();
	}

	@Test
	public void shouldNotContainAnyTracingInfoInTheLogs() {
		log.info("hello");

		BDDAssertions.then(this.capture.toString()).doesNotContain("[foo");
	}

	@EnableAutoConfiguration
	@Configuration
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
