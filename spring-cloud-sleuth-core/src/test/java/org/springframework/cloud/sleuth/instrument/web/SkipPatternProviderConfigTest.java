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

package org.springframework.cloud.sleuth.instrument.web;

import java.util.Arrays;
import java.util.Optional;
import java.util.regex.Pattern;

import org.junit.Test;

import org.springframework.boot.actuate.autoconfigure.endpoint.web.WebEndpointProperties;
import org.springframework.boot.actuate.autoconfigure.web.server.ManagementServerProperties;
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
	public void should_return_empty_when_server_props_have_no_context_path()
			throws Exception {
		Optional<Pattern> pattern = new TraceWebAutoConfiguration.ServerSkipPatternProviderConfig()
				.skipPatternForServerProperties(new ServerProperties(),
						new WebEndpointProperties())
				.skipPattern();

		then(pattern).isEmpty();
	}

	@Test
	public void should_return_server_props_with_context_path() throws Exception {
		ServerProperties properties = new ServerProperties();
		properties.getServlet().setContextPath("foo");

		Optional<Pattern> pattern = new TraceWebAutoConfiguration.ServerSkipPatternProviderConfig()
				.skipPatternForServerProperties(properties, new WebEndpointProperties())
				.skipPattern();

		then(pattern).isNotEmpty();
		then(pattern.get().pattern()).isEqualTo("foo/actuator.*");
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

}