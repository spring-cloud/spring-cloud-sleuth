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

package org.springframework.cloud.sleuth.autoconfig;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.beans.factory.BeanCurrentlyInCreationException;
import org.springframework.boot.actuate.autoconfigure.endpoint.web.WebEndpointProperties;
import org.springframework.boot.actuate.autoconfigure.web.server.ConditionalOnManagementPort;
import org.springframework.boot.actuate.autoconfigure.web.server.ManagementPortType;
import org.springframework.boot.actuate.autoconfigure.web.server.ManagementServerProperties;
import org.springframework.boot.actuate.endpoint.EndpointsSupplier;
import org.springframework.boot.actuate.endpoint.web.ExposableWebEndpoint;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.sleuth.instrument.web.SkipPatternProvider;
import org.springframework.cloud.sleuth.instrument.web.SleuthWebProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration
 * Auto-configuration} that sets up common building blocks for both reactive and servlet
 * based web application.
 *
 * @author Marcin Grzejszczak
 * @author Tim Ysewyn
 * @since 1.0.0
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(value = "spring.sleuth.web.enabled", matchIfMissing = true)
@EnableConfigurationProperties(SleuthWebProperties.class)
class SkipPatternConfiguration {

	@Bean
	@ConditionalOnMissingBean
	SkipPatternProvider sleuthSkipPatternProvider(@Nullable List<SingleSkipPattern> patterns) {
		if (patterns == null || patterns.isEmpty()) {
			return null;
		}

		// Actuator endpoints are queried to make the default skip pattern. There's an
		// edge case where actuator endpoints indirectly reference the still constructing
		// HttpTracing bean. Ex: an instrumented client could cause a cyclic dep.
		//
		// Below optimizes for the opposite: that custom actuator endpoints are not in
		// use. This allows configuration to be eagerly parsed, allowing any errors to
		// surface earlier. In the case there is a cyclic dep, this parsing becomes lazy,
		// deferring any errors creating the skip pattern.
		//
		// See #1679
		try {
			Pattern result = consolidateSkipPatterns(patterns);
			if (result == null) {
				return null;
			}
			return () -> result;
		}
		catch (BeanCurrentlyInCreationException e) {
			// Most likely, there is an actuator endpoint that indirectly references an
			// instrumented HTTP client.
			return () -> consolidateSkipPatterns(patterns);
		}
	}

	@Nullable
	static Pattern consolidateSkipPatterns(List<SingleSkipPattern> patterns) {
		List<Pattern> presentPatterns = patterns.stream().map(SingleSkipPattern::skipPattern)
				.filter(Optional::isPresent).map(Optional::get).collect(Collectors.toList());
		if (presentPatterns.isEmpty()) {
			return null;
		}
		if (presentPatterns.size() == 1) {
			return presentPatterns.get(0);
		}

		StringJoiner joiner = new StringJoiner("|");
		for (Pattern pattern : presentPatterns) {
			String s = pattern.pattern();
			joiner.add(s);
		}
		return Pattern.compile(joiner.toString());
	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(ManagementServerProperties.class)
	@ConditionalOnProperty(value = "spring.sleuth.web.ignoreAutoConfiguredSkipPatterns", havingValue = "false",
			matchIfMissing = true)
	protected static class ManagementSkipPatternProviderConfig {

		/**
		 * Sets or appends {@link ManagementServerProperties#getServlet()} to the skip
		 * pattern. If neither is available then sets the default one
		 * @param managementServerProperties properties
		 * @return optional skip pattern
		 */
		static Optional<Pattern> getPatternForManagementServerProperties(Environment environment,
				ManagementServerProperties managementServerProperties) {
			String contextPath = managementServerProperties.getServlet().getContextPath();
			if (StringUtils.hasText(contextPath)) {
				return Optional.of(Pattern.compile(environment.resolvePlaceholders(contextPath) + ".*"));
			}
			return Optional.empty();
		}

		@Bean
		@ConditionalOnBean(ManagementServerProperties.class)
		public SingleSkipPattern skipPatternForManagementServerProperties(Environment environment,
				final ManagementServerProperties managementServerProperties) {
			return () -> getPatternForManagementServerProperties(environment, managementServerProperties);
		}

	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass({ ServerProperties.class, EndpointsSupplier.class, ExposableWebEndpoint.class })
	@ConditionalOnBean(ServerProperties.class)
	@ConditionalOnProperty(value = "spring.sleuth.web.ignoreAutoConfiguredSkipPatterns", havingValue = "false",
			matchIfMissing = true)
	protected static class ActuatorSkipPatternProviderConfig {

		static Optional<Pattern> getEndpointsPatterns(Environment environment, String contextPath,
				WebEndpointProperties webEndpointProperties,
				EndpointsSupplier<ExposableWebEndpoint> endpointsSupplier) {
			Collection<ExposableWebEndpoint> endpoints = endpointsSupplier.getEndpoints();
			if (endpoints.isEmpty()) {
				return Optional.empty();
			}
			String basePath = webEndpointProperties.getBasePath();
			String pattern = patternFromEndpoints(contextPath, endpoints, basePath);
			if (StringUtils.hasText(pattern)) {
				return Optional.of(Pattern.compile(environment.resolvePlaceholders(pattern)));
			}
			return Optional.empty();
		}

		private static String patternFromEndpoints(String contextPath, Collection<ExposableWebEndpoint> endpoints,
				String basePath) {
			StringJoiner joiner = new StringJoiner("|", getPathPrefix(contextPath, basePath),
					getPathSuffix(contextPath, basePath));
			for (ExposableWebEndpoint endpoint : endpoints) {
				String path = endpoint.getRootPath();
				String paths = path + "|" + path + "/.*";
				joiner.add(paths);
			}
			return joiner.toString();
		}

		private static String getPathPrefix(String contextPath, String actuatorBasePath) {
			String result = "";
			if (StringUtils.hasText(contextPath)) {
				result += contextPath;
			}
			if (!actuatorBasePath.equals("/")) {
				result += actuatorBasePath;
			}
			boolean ignoreBase = StringUtils.hasText(result) && !result.equals("/");
			String suffix = "/(";
			if (ignoreBase) {
				suffix = "(/|" + suffix;
			}
			return result + suffix;
		}

		private static String getPathSuffix(String contextPath, String actuatorBasePath) {
			String result = ")";
			if (StringUtils.hasText(contextPath)
					|| (StringUtils.hasText(actuatorBasePath) && !"/".equals(actuatorBasePath))) {
				result += ")?";
			}
			return result;
		}

		@Bean
		@ConditionalOnManagementPort(ManagementPortType.SAME)
		SingleSkipPattern skipPatternForActuatorEndpointsSamePort(Environment environment,
				final ServerProperties serverProperties, final WebEndpointProperties webEndpointProperties,
				final EndpointsSupplier<ExposableWebEndpoint> endpointsSupplier) {
			return () -> getEndpointsPatterns(environment, serverProperties.getServlet().getContextPath(),
					webEndpointProperties, endpointsSupplier);
		}

		@Bean
		@ConditionalOnManagementPort(ManagementPortType.DIFFERENT)
		@ConditionalOnProperty(name = "management.server.servlet.context-path", havingValue = "/",
				matchIfMissing = true)
		SingleSkipPattern skipPatternForActuatorEndpointsDifferentPort(Environment environment,
				final ServerProperties serverProperties, final WebEndpointProperties webEndpointProperties,
				final EndpointsSupplier<ExposableWebEndpoint> endpointsSupplier) {
			return () -> getEndpointsPatterns(environment, null, webEndpointProperties, endpointsSupplier);
		}

	}

	@Configuration(proxyBeanMethods = false)
	static class DefaultSkipPatternConfig {

		@Bean
		SingleSkipPattern defaultSkipPatternBean(Environment environment, SleuthWebProperties sleuthWebProperties) {
			String skipPattern = sleuthWebProperties.getSkipPattern();
			String left = StringUtils.hasText(skipPattern) ? environment.resolvePlaceholders(skipPattern) : skipPattern;
			String additionalSkipPattern = sleuthWebProperties.getAdditionalSkipPattern();
			String right = StringUtils.hasText(additionalSkipPattern)
					? environment.resolvePlaceholders(additionalSkipPattern) : additionalSkipPattern;
			Pattern pattern = combinePatterns(left, right);
			return () -> Optional.ofNullable(pattern);
		}

		private static Pattern combinePatterns(String left, String right) {
			if (left == null && right == null) {
				return null;
			}
			else if (left == null) {
				return Pattern.compile(right);
			}
			else if (right == null) {
				return Pattern.compile(left);
			}
			return Pattern.compile(left + "|" + right);
		}

	}

}
