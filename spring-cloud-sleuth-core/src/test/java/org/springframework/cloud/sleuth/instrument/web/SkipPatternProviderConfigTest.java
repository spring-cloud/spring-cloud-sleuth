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

package org.springframework.cloud.sleuth.instrument.web;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.regex.Pattern;

import org.junit.Test;

import org.springframework.boot.actuate.autoconfigure.endpoint.web.WebEndpointProperties;
import org.springframework.boot.actuate.autoconfigure.web.server.ManagementServerProperties;
import org.springframework.boot.actuate.endpoint.EndpointId;
import org.springframework.boot.actuate.endpoint.EndpointsSupplier;
import org.springframework.boot.actuate.endpoint.web.ExposableWebEndpoint;
import org.springframework.boot.actuate.endpoint.web.WebOperation;
import org.springframework.boot.autoconfigure.web.ServerProperties;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author Marcin Grzejszczak
 */
public class SkipPatternProviderConfigTest {

	@Test
	public void should_pick_skip_pattern_from_sleuth_properties() throws Exception {
		SleuthWebProperties sleuthWebProperties = new SleuthWebProperties();
		sleuthWebProperties.setSkipPattern("foo.*|bar.*");
		Pattern pattern = new TraceWebAutoConfiguration.DefaultSkipPatternConfig()
				.defaultSkipPatternBean(sleuthWebProperties).skipPattern().get();

		then(pattern.pattern()).isEqualTo("foo.*|bar.*");
	}

	@Test
	public void should_combine_skip_pattern_and_additional_pattern_when_all_are_not_empty()
			throws Exception {
		SleuthWebProperties sleuthWebProperties = new SleuthWebProperties();
		sleuthWebProperties.setSkipPattern("foo.*|bar.*");
		sleuthWebProperties.setAdditionalSkipPattern("baz.*|faz.*");
		Pattern pattern = new TraceWebAutoConfiguration.DefaultSkipPatternConfig()
				.defaultSkipPatternBean(sleuthWebProperties).skipPattern().get();

		then(pattern.pattern()).isEqualTo("foo.*|bar.*|baz.*|faz.*");
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
		ManagementServerProperties properties = new ManagementServerProperties();
		properties.getServlet().setContextPath("foo");

		Optional<Pattern> pattern = new TraceWebAutoConfiguration.ManagementSkipPatternProviderConfig()
				.skipPatternForManagementServerProperties(properties).skipPattern();

		then(pattern).isNotEmpty();
		then(pattern.get().pattern()).isEqualTo("foo.*");
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
		ServerProperties properties = new ServerProperties();
		WebEndpointProperties webEndpointProperties = new WebEndpointProperties();
		EndpointsSupplier<ExposableWebEndpoint> endpointsSupplier = () -> {
			ExposableWebEndpoint infoEndpoint = createEndpoint("info");
			ExposableWebEndpoint healthEndpoint = createEndpoint("health");

			return Arrays.asList(infoEndpoint, healthEndpoint);
		};

		Optional<Pattern> pattern = new TraceWebAutoConfiguration.ActuatorSkipPatternProviderConfig()
				.skipPatternForActuatorEndpointsSamePort(properties,
						webEndpointProperties, endpointsSupplier)
				.skipPattern();

		then(pattern).isNotEmpty();
		then(pattern.get().pattern())
				.isEqualTo("/actuator(/|/(info|info/.*|health|health/.*))?");
	}

	@Test
	public void should_return_endpoints_with_context_path() {
		WebEndpointProperties webEndpointProperties = new WebEndpointProperties();
		ServerProperties properties = new ServerProperties();
		properties.getServlet().setContextPath("foo");

		EndpointsSupplier<ExposableWebEndpoint> endpointsSupplier = () -> {
			ExposableWebEndpoint infoEndpoint = createEndpoint("info");
			ExposableWebEndpoint healthEndpoint = createEndpoint("health");

			return Arrays.asList(infoEndpoint, healthEndpoint);
		};

		Optional<Pattern> pattern = new TraceWebAutoConfiguration.ActuatorSkipPatternProviderConfig()
				.skipPatternForActuatorEndpointsSamePort(properties,
						webEndpointProperties, endpointsSupplier)
				.skipPattern();

		then(pattern).isNotEmpty();
		then(pattern.get().pattern())
				.isEqualTo("foo/actuator(/|/(info|info/.*|health|health/.*))?");
	}

	@Test
	public void should_return_endpoints_without_context_path_and_base_path_set_to_root() {
		ServerProperties properties = new ServerProperties();
		WebEndpointProperties webEndpointProperties = new WebEndpointProperties();
		webEndpointProperties.setBasePath("/");

		EndpointsSupplier<ExposableWebEndpoint> endpointsSupplier = () -> {
			ExposableWebEndpoint infoEndpoint = createEndpoint("info");
			ExposableWebEndpoint healthEndpoint = createEndpoint("health");

			return Arrays.asList(infoEndpoint, healthEndpoint);
		};

		Optional<Pattern> pattern = new TraceWebAutoConfiguration.ActuatorSkipPatternProviderConfig()
				.skipPatternForActuatorEndpointsSamePort(properties,
						webEndpointProperties, endpointsSupplier)
				.skipPattern();

		then(pattern).isNotEmpty();
		then(pattern.get().pattern()).isEqualTo("/(info|info/.*|health|health/.*)");
	}

	@Test
	public void should_return_endpoints_with_context_path_and_base_path_set_to_root() {
		WebEndpointProperties webEndpointProperties = new WebEndpointProperties();
		webEndpointProperties.setBasePath("/");
		ServerProperties properties = new ServerProperties();
		properties.getServlet().setContextPath("foo");

		EndpointsSupplier<ExposableWebEndpoint> endpointsSupplier = () -> {
			ExposableWebEndpoint infoEndpoint = createEndpoint("info");
			ExposableWebEndpoint healthEndpoint = createEndpoint("health");

			return Arrays.asList(infoEndpoint, healthEndpoint);
		};

		Optional<Pattern> pattern = new TraceWebAutoConfiguration.ActuatorSkipPatternProviderConfig()
				.skipPatternForActuatorEndpointsSamePort(properties,
						webEndpointProperties, endpointsSupplier)
				.skipPattern();

		then(pattern).isNotEmpty();
		then(pattern.get().pattern()).isEqualTo("foo(/|/(info|info/.*|health|health/.*))?");
	}

	@Test
	public void should_return_endpoints_with_actuator_context_path_set_to_root() {
		WebEndpointProperties webEndpointProperties = new WebEndpointProperties();
		webEndpointProperties.setBasePath("/");
		ServerProperties properties = new ServerProperties();
		properties.getServlet().setContextPath("foo");

		EndpointsSupplier<ExposableWebEndpoint> endpointsSupplier = () -> {
			ExposableWebEndpoint infoEndpoint = createEndpoint("info");
			ExposableWebEndpoint healthEndpoint = createEndpoint("health");

			return Arrays.asList(infoEndpoint, healthEndpoint);
		};

		Optional<Pattern> patternDifferentPort = new TraceWebAutoConfiguration.ActuatorSkipPatternProviderConfig()
				.skipPatternForActuatorEndpointsDifferentPort(properties,
						webEndpointProperties, endpointsSupplier)
				.skipPattern();

		then(patternDifferentPort).isNotEmpty();
		then(patternDifferentPort.get().pattern())
				.isEqualTo("/(info|info/.*|health|health/.*)");

		Optional<Pattern> patternSamePort = new TraceWebAutoConfiguration.ActuatorSkipPatternProviderConfig()
				.skipPatternForActuatorEndpointsSamePort(properties,
						webEndpointProperties, endpointsSupplier)
				.skipPattern();

		then(patternSamePort).isNotEmpty();
		then(patternSamePort.get().pattern())
				.isEqualTo("foo(/|/(info|info/.*|health|health/.*))?");
	}

	@Test
	public void should_return_endpoints_with_actuator_context_path_only() {
		WebEndpointProperties webEndpointProperties = new WebEndpointProperties();
		webEndpointProperties.setBasePath("/mgt");
		ServerProperties properties = new ServerProperties();
		properties.getServlet().setContextPath("foo");

		EndpointsSupplier<ExposableWebEndpoint> endpointsSupplier = () -> {
			ExposableWebEndpoint infoEndpoint = createEndpoint("info");
			ExposableWebEndpoint healthEndpoint = createEndpoint("health");

			return Arrays.asList(infoEndpoint, healthEndpoint);
		};

		Optional<Pattern> patternDifferentPort = new TraceWebAutoConfiguration.ActuatorSkipPatternProviderConfig()
				.skipPatternForActuatorEndpointsDifferentPort(properties,
						webEndpointProperties, endpointsSupplier)
				.skipPattern();

		then(patternDifferentPort).isNotEmpty();
		then(patternDifferentPort.get().pattern())
				.isEqualTo("/mgt(/|/(info|info/.*|health|health/.*))?");

		Optional<Pattern> patternSamePort = new TraceWebAutoConfiguration.ActuatorSkipPatternProviderConfig()
				.skipPatternForActuatorEndpointsSamePort(properties,
						webEndpointProperties, endpointsSupplier)
				.skipPattern();

		then(patternSamePort).isNotEmpty();
		then(patternSamePort.get().pattern())
				.isEqualTo("foo/mgt(/|/(info|info/.*|health|health/.*))?");
	}

	@Test
	public void should_return_endpoints_with_actuator_default_context_path() {
		WebEndpointProperties webEndpointProperties = new WebEndpointProperties();
		ServerProperties properties = new ServerProperties();
		properties.getServlet().setContextPath("/foo");
		properties.setPort(8080);

		EndpointsSupplier<ExposableWebEndpoint> endpointsSupplier = () -> {
			ExposableWebEndpoint infoEndpoint = createEndpoint("info");
			ExposableWebEndpoint healthEndpoint = createEndpoint("health");

			return Arrays.asList(infoEndpoint, healthEndpoint);
		};

		Optional<Pattern> patternDifferentPort = new TraceWebAutoConfiguration.ActuatorSkipPatternProviderConfig()
				.skipPatternForActuatorEndpointsDifferentPort(properties,
						webEndpointProperties, endpointsSupplier)
				.skipPattern();

		then(patternDifferentPort).isNotEmpty();
		then(patternDifferentPort.get().pattern())
				.isEqualTo("/actuator(/|/(info|info/.*|health|health/.*))?");

		Optional<Pattern> patternSamePort = new TraceWebAutoConfiguration.ActuatorSkipPatternProviderConfig()
				.skipPatternForActuatorEndpointsSamePort(properties,
						webEndpointProperties, endpointsSupplier)
				.skipPattern();

		then(patternSamePort).isNotEmpty();
		then(patternSamePort.get().pattern())
				.isEqualTo("/foo/actuator(/|/(info|info/.*|health|health/.*))?");
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

	private ExposableWebEndpoint createEndpoint(final String name) {
		return new ExposableWebEndpoint() {

			@Override
			public String getRootPath() {
				return name;
			}

			@Override
			public EndpointId getEndpointId() {
				return EndpointId.of(name);
			}

			@Override
			public boolean isEnableByDefault() {
				return false;
			}

			@Override
			public Collection<WebOperation> getOperations() {
				return null;
			}
		};
	}

}
