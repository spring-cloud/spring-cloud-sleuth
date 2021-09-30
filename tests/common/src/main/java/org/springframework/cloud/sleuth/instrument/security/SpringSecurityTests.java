/*
 * Copyright 2021-2021 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.security;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.cloud.sleuth.exporter.FinishedSpan;
import org.springframework.cloud.sleuth.test.TestSpanHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.ListeningSecurityContextHolderStrategy;
import org.springframework.security.core.context.SecurityContextChangedListener;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.BDDAssertions.then;
import static org.springframework.cloud.sleuth.Span.Kind.SERVER;

/**
 * @author Jonatan Ivanov
 */
@ContextConfiguration(classes = SpringSecurityTests.Config.class)
public abstract class SpringSecurityTests {

	@Autowired
	TestSpanHandler testSpanHandler;

	@Autowired
	TestRestTemplate restTemplate;

	@BeforeEach
	void setUp() {
		testSpanHandler.clear();
	}

	@Test
	void authenticated_user_should_trigger_events() {
		ResponseEntity<String> entity = restTemplate.withBasicAuth("user", "password").getForEntity("/", String.class);

		then(entity.getStatusCode().is2xxSuccessful()).isTrue();
		then(entity.getBody()).isEqualTo("authenticated");

		List<Map.Entry<Long, String>> authEvents = getAuthEvents(testSpanHandler.reportedSpans());
		for (int i = 0; i < authEvents.size(); i += 2) {
			String setEvent = authEvents.get(i).getValue();
			then(setEvent).isEqualTo("Authentication set UsernamePasswordAuthenticationToken[USER]");
			String clearEvent = authEvents.get(i + 1).getValue();
			then(clearEvent).isEqualTo("Authentication cleared UsernamePasswordAuthenticationToken[USER]");
		}
	}

	@Test
	void anonymous_user_should_trigger_events() {
		ResponseEntity<String> entity = restTemplate.getForEntity("/", String.class);

		then(entity.getStatusCode().is2xxSuccessful()).isTrue();
		then(entity.getBody()).contains("html", "form");

		List<Map.Entry<Long, String>> authEvents = getAuthEvents(testSpanHandler.reportedSpans());
		for (int i = 0; i < authEvents.size(); i += 2) {
			String setEvent = authEvents.get(i).getValue();
			then(setEvent).isEqualTo("Authentication set AnonymousAuthenticationToken[ROLE_ANONYMOUS]");
			String clearEvent = authEvents.get(i + 1).getValue();
			then(clearEvent).isEqualTo("Authentication cleared AnonymousAuthenticationToken[ROLE_ANONYMOUS]");
		}
	}

	private List<Map.Entry<Long, String>> getAuthEvents(List<FinishedSpan> spans) {
		return spans.stream().filter(span -> span.getKind() == SERVER).map(FinishedSpan::getEvents)
				.flatMap(Collection::stream).filter(event -> event.getValue().contains("Authentication"))
				.collect(Collectors.toList());
	}

	@EnableAutoConfiguration(
			excludeName = "org.springframework.cloud.sleuth.autoconfig.instrument.web.client.TraceWebClientAutoConfiguration")
	@Configuration(proxyBeanMethods = false)
	static class Config {

		// TODO: Remove this after Spring Boot auto-configuration is available
		Config(List<SecurityContextChangedListener> listeners) {
			SecurityContextHolderStrategy strategy = new ListeningSecurityContextHolderStrategy(
					SecurityContextHolder.getContextHolderStrategy(), listeners);
			SecurityContextHolder.setContextHolderStrategy(strategy);
		}

		@Bean
		UserDetailsService userDetailsService() {
			return new InMemoryUserDetailsManager(User.withDefaultPasswordEncoder().username("user")
					.password("password").authorities("USER").build());
		}

		@Bean
		HomeController homeController() {
			return new HomeController();
		}

	}

	@RestController
	static class HomeController {

		@GetMapping
		String home() {
			return "authenticated";
		}

	}

}
