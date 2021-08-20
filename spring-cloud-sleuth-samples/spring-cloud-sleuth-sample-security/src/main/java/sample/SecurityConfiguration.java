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

package sample;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.context.SecurityContextChangedListener;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

import static org.springframework.security.config.Customizer.withDefaults;

/**
 * Spring Security config
 *
 * @author Jonatan Ivanov
 * @since 3.1.0
 */
@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

	public SecurityConfiguration(List<SecurityContextChangedListener> securityContextChangedListeners) {
		securityContextChangedListeners.forEach(SecurityContextHolder::addListener);
	}

	@Bean
	SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		return http.authorizeRequests(requests -> requests
						.antMatchers("/favicon.ico").permitAll()
						.anyRequest().authenticated()
				)
				.httpBasic(withDefaults())
				.formLogin(withDefaults())
				.build();
	}

	@Bean
	public InMemoryUserDetailsManager userDetailsService() {
		return new InMemoryUserDetailsManager(User.withDefaultPasswordEncoder().username("user").password("password").roles("USER").build());
	}

}
