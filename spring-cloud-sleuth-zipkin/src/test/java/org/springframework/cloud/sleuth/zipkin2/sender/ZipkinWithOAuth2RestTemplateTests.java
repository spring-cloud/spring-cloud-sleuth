/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.sleuth.zipkin2.sender;

import org.junit.Test;
import org.junit.runner.RunWith;
import zipkin2.reporter.Sender;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.integration.IntegrationAutoConfiguration;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration;
import org.springframework.cloud.sleuth.zipkin2.ZipkinAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.OAuth2RestTemplate;
import org.springframework.security.oauth2.client.resource.OAuth2ProtectedResourceDetails;
import org.springframework.security.oauth2.client.token.grant.client.ClientCredentialsResourceDetails;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = ZipkinWithOAuth2RestTemplateTests.Config.class)
@TestPropertySource(properties = "spring.zipkin.sender.type=web")
public class ZipkinWithOAuth2RestTemplateTests {

	@Autowired
	private Sender sender;

	@Test
	public void shouldUseOAuth2RestTemplateWhenInjectingBean() {
		assertThat(sender).isInstanceOf(RestTemplateSender.class);
		RestTemplateSender restTemplateSender = (RestTemplateSender) sender;
		assertThat(restTemplateSender.restTemplate)
				.isInstanceOf(OAuth2RestTemplate.class);
	}

	@EnableAutoConfiguration(exclude = { IntegrationAutoConfiguration.class,
			ReactiveSecurityAutoConfiguration.class })
	@Import(MyConfig.class)
	static class Config {

	}

	// tag::use_oauth2_resttemplate[]

	@Configuration
	protected static class MyConfig {

		@Bean(name = ZipkinAutoConfiguration.OAUTH2_RESOURCE_BEAN_NAME)
		OAuth2ProtectedResourceDetails resourceDetails() {
			return new ClientCredentialsResourceDetails();
		}

	}

	// end::override_default_beans[]

}
