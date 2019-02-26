/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.web;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.regex.Pattern;

import org.junit.Test;

import org.springframework.boot.actuate.autoconfigure.endpoint.EndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.endpoint.web.WebEndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.endpoint.web.WebEndpointProperties;
import org.springframework.boot.actuate.autoconfigure.health.HealthEndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.health.HealthIndicatorAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.info.InfoEndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.web.server.ManagementContextAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.web.server.ManagementServerProperties;
import org.springframework.boot.actuate.endpoint.EndpointsSupplier;
import org.springframework.boot.actuate.endpoint.web.ExposableWebEndpoint;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.cloud.sleuth.autoconfig.TraceAutoConfiguration;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author Marcin Grzejszczak
 */
public class SkipPatternProviderConfigTest {

	private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(TraceAutoConfiguration.class,
					TraceWebAutoConfiguration.class));

	@Test
	public void should_pick_skip_pattern_from_sleuth_properties() throws Exception {
		contextRunner.withPropertyValues("spring.sleuth.web.skip-pattern=foo.*|bar.*")
				.run(context -> {
					SkipPatternProvider skipPatternProvider = context
							.getBean(SkipPatternProvider.class);
					Pattern pattern = skipPatternProvider.skipPattern();
					then(pattern.pattern()).isEqualTo("foo.*|bar.*");
				});
	}

	@Test
	public void should_combine_skip_pattern_and_additional_pattern_when_all_are_not_empty() {
		contextRunner
				.withPropertyValues("spring.sleuth.web.skip-pattern=foo.*|bar.*",
						"spring.sleuth.web.additional-skip-pattern=baz.*|faz.*")
				.run(context -> {
					SkipPatternProvider skipPatternProvider = context
							.getBean(SkipPatternProvider.class);
					Pattern pattern = skipPatternProvider.skipPattern();
					then(pattern.pattern()).isEqualTo("foo.*|bar.*|baz.*|faz.*");
				});
	}

	@Test
	public void should_return_empty_when_management_context_has_no_context_path()
			throws Exception {
		Optional<Pattern> pattern = new TraceWebAutoConfiguration.ManagementSkipPatternProviderConfig()
				.skipPatternForManagementServerProperties(
						new ManagementServerProperties())
				.skipPattern();

		then(pattern).isEmpty();
	}

	@Test
	public void should_return_management_context_with_context_path() throws Exception {
		contextRunner
				.withConfiguration(UserConfigurations.of(
						ManagementContextAutoConfiguration.class, EndpointConfig.class))
				.withPropertyValues("management.server.servlet.context-path=foo")
				.run(context -> {
					SkipPatternProvider skipPatternProvider = context
							.getBean(SkipPatternProvider.class);
					Pattern pattern = skipPatternProvider.skipPattern();
					then(pattern.pattern()).contains("|foo.*");
				});
	}

	@Test
	public void should_return_empty_when_no_endpoints() {
		EndpointsSupplier<ExposableWebEndpoint> endpointsSupplier = Collections::emptyList;
		Optional<Pattern> pattern = new TraceWebAutoConfiguration.ActuatorSkipPatternProviderConfig()
				.skipPatternForActuatorEndpointsSamePort(new ServerProperties(),
						new WebEndpointProperties(), endpointsSupplier)
				.skipPattern();

		then(pattern).isEmpty();
	}

	@Test
	public void should_return_endpoints_without_context_path() {
		contextRunner.withConfiguration(UserConfigurations.of(EndpointConfig.class))
				.withPropertyValues("management.endpoint.health.enabled=true",
						"management.endpoint.info.enabled=true",
						"management.endpoints.web.exposure.include=info,health")
				.run(context -> {
					SkipPatternProvider skipPatternProvider = context
							.getBean(SkipPatternProvider.class);
					Pattern pattern = skipPatternProvider.skipPattern();
					then(pattern.pattern())
							.contains("/actuator/(health|health/.*|info|info/.*)");
				});
	}

	@Test
	public void should_return_endpoints_with_context_path() {
		contextRunner.withConfiguration(UserConfigurations.of(EndpointConfig.class))
				.withPropertyValues("management.endpoint.health.enabled=true",
						"management.endpoint.info.enabled=true",
						"management.endpoints.web.exposure.include=info,health",
						"server.servlet.context-path=foo")
				.run(context -> {
					SkipPatternProvider skipPatternProvider = context
							.getBean(SkipPatternProvider.class);
					Pattern pattern = skipPatternProvider.skipPattern();
					then(pattern.pattern())
							.contains("foo/actuator/(health|health/.*|info|info/.*)");
				});
	}

	@Test
	public void should_return_endpoints_without_context_path_and_base_path_set_to_root() {
		contextRunner.withConfiguration(UserConfigurations.of(EndpointConfig.class))
				.withPropertyValues("management.endpoint.health.enabled=true",
						"management.endpoint.info.enabled=true",
						"management.endpoints.web.exposure.include=info,health",
						"management.endpoints.web.base-path=/")
				.run(context -> {
					SkipPatternProvider skipPatternProvider = context
							.getBean(SkipPatternProvider.class);
					Pattern pattern = skipPatternProvider.skipPattern();
					then(pattern.pattern()).contains("/(health|health/.*|info|info/.*)");
				});
	}

	@Test
	public void should_return_endpoints_with_context_path_and_base_path_set_to_root() {
		contextRunner.withConfiguration(UserConfigurations.of(EndpointConfig.class))
				.withPropertyValues("management.endpoint.health.enabled=true",
						"management.endpoint.info.enabled=true",
						"management.endpoints.web.exposure.include=info,health",
						"management.endpoints.web.base-path=/",
						"server.servlet.context-path=foo")
				.run(context -> {
					SkipPatternProvider skipPatternProvider = context
							.getBean(SkipPatternProvider.class);
					Pattern pattern = skipPatternProvider.skipPattern();
					then(pattern.pattern())
							.contains("foo/(health|health/.*|info|info/.*)");
				});
	}

	@Test
	public void should_return_endpoints_with_context_path_and_base_path_set_to_root_different_port() {
		contextRunner.withConfiguration(UserConfigurations.of(EndpointConfig.class))
				.withPropertyValues("management.endpoint.health.enabled=true",
						"management.endpoint.info.enabled=true",
						"management.endpoints.web.exposure.include=info,health",
						"management.endpoints.web.base-path=/",
						"management.servet.port=0", "server.servlet.context-path=foo")
				.run(context -> {
					SkipPatternProvider skipPatternProvider = context
							.getBean(SkipPatternProvider.class);
					Pattern pattern = skipPatternProvider.skipPattern();
					then(pattern.pattern()).contains("/(health|health/.*|info|info/.*)");
				});
	}

	@Test
	public void should_return_endpoints_with_actuator_context_path_only() {
		contextRunner.withConfiguration(UserConfigurations.of(EndpointConfig.class))
				.withPropertyValues("management.endpoint.health.enabled=true",
						"management.endpoint.info.enabled=true",
						"management.endpoints.web.exposure.include=info,health",
						"management.endpoints.web.base-path=/mgt",
						"server.servlet.context-path=foo")
				.run(context -> {
					SkipPatternProvider skipPatternProvider = context
							.getBean(SkipPatternProvider.class);
					Pattern pattern = skipPatternProvider.skipPattern();
					then(pattern.pattern())
							.contains("foo/mgt/(health|health/.*|info|info/.*)");
				});
	}

	@Test
	public void should_return_endpoints_with_actuator_default_context_path() {
		contextRunner.withConfiguration(UserConfigurations.of(EndpointConfig.class))
				.withPropertyValues("management.endpoint.health.enabled=true",
						"management.endpoint.info.enabled=true",
						"management.endpoints.web.exposure.include=info,health",
						"server.servlet.context-path=foo")
				.run(context -> {
					SkipPatternProvider skipPatternProvider = context
							.getBean(SkipPatternProvider.class);
					Pattern pattern = skipPatternProvider.skipPattern();
					then(pattern.pattern())
							.contains("foo/actuator/(health|health/.*|info|info/.*)");
				});
	}

	@Test
	public void should_return_endpoints_with_actuator_default_context_path_different_port() {
		contextRunner.withConfiguration(UserConfigurations.of(EndpointConfig.class))
				.withPropertyValues("management.endpoint.health.enabled=true",
						"management.endpoint.info.enabled=true",
						"management.endpoints.web.exposure.include=info,health",
						"management.servet.port=0", "server.servlet.context-path=foo")
				.run(context -> {
					SkipPatternProvider skipPatternProvider = context
							.getBean(SkipPatternProvider.class);
					Pattern pattern = skipPatternProvider.skipPattern();
					then(pattern.pattern())
							.contains("/actuator/(health|health/.*|info|info/.*)");
				});
	}

	@Test
	public void should_return_endpoints_with_actuator_context_path_only_different_port() {
		contextRunner.withConfiguration(UserConfigurations.of(EndpointConfig.class))
				.withPropertyValues("management.endpoint.health.enabled=true",
						"management.endpoint.info.enabled=true",
						"management.endpoints.web.exposure.include=info,health",
						"management.endpoints.web.base-path=/mgt",
						"management.servet.port=0", "server.servlet.context-path=foo")
				.run(context -> {
					SkipPatternProvider skipPatternProvider = context
							.getBean(SkipPatternProvider.class);
					Pattern pattern = skipPatternProvider.skipPattern();
					then(pattern.pattern())
							.contains("/mgt/(health|health/.*|info|info/.*)");
				});
	}

	@Test
	public void should_return_endpoints_with_context_path_different_port() {
		contextRunner.withConfiguration(UserConfigurations.of(EndpointConfig.class))
				.withPropertyValues("management.endpoint.health.enabled=true",
						"management.endpoint.info.enabled=true",
						"management.endpoints.web.exposure.include=info,health",
						"management.servet.port=0", "server.servlet.context-path=foo")
				.run(context -> {
					SkipPatternProvider skipPatternProvider = context
							.getBean(SkipPatternProvider.class);
					Pattern pattern = skipPatternProvider.skipPattern();
					then(pattern.pattern())
							.contains("/actuator/(health|health/.*|info|info/.*)");
				});
	}

	@Test
	public void should_combine_skip_patterns_from_list() throws Exception {
		TraceWebAutoConfiguration configuration = new TraceWebAutoConfiguration();
		configuration.patterns.addAll(Arrays.asList(foo(), bar()));

		Pattern pattern = configuration.sleuthSkipPatternProvider().skipPattern();

		then(pattern.pattern()).isEqualTo("foo|bar");
	}

	private SingleSkipPattern foo() {
		return () -> Optional.of(Pattern.compile("foo"));
	}

	private SingleSkipPattern bar() {
		return () -> Optional.of(Pattern.compile("bar"));
	}

	@Configuration
	@ImportAutoConfiguration({ DispatcherServletAutoConfiguration.class,
			InfoEndpointAutoConfiguration.class, HealthIndicatorAutoConfiguration.class,
			HealthEndpointAutoConfiguration.class, EndpointAutoConfiguration.class,
			WebEndpointAutoConfiguration.class })
	@EnableConfigurationProperties(ServerProperties.class)
	static class EndpointConfig {

	}
}
