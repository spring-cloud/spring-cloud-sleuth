/*
 * Copyright 2013-2015 the original author or authors.
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

import java.util.Random;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.embedded.FilterRegistrationBean;
import org.springframework.cloud.sleuth.TraceAccessor;
import org.springframework.cloud.sleuth.TraceManager;
import org.springframework.cloud.sleuth.autoconfig.TraceAutoConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Registers beans that add tracing to requests
 *
 * @author Tomasz Nurkewicz, 4financeIT
 * @author Marcin Grzejszczak, 4financeIT
 * @author Michal Chmielarz, 4financeIT
 * @author Spencer Gibb
 */
@Configuration
@ConditionalOnProperty(value = "spring.sleuth.web.enabled", matchIfMissing = true)
@ConditionalOnWebApplication
@ConditionalOnBean(TraceManager.class)
@AutoConfigureAfter(TraceAutoConfiguration.class)
public class TraceWebAutoConfiguration {

	/**
	 * Pattern for URLs that should be skipped in tracing
	 */
	@Value("${spring.sleuth.instrument.web.skipPattern:}")
	private String skipPattern;

	@Autowired
	private TraceManager traceManager;

	@Autowired
	private TraceAccessor accessor;

	@Bean
	public TraceWebAspect traceWebAspect() {
		return new TraceWebAspect(this.traceManager, this.accessor);
	}

	@Bean
	@ConditionalOnMissingBean
	public TraceFilter traceFilter(ApplicationEventPublisher publisher, Random random) {
		Pattern pattern = StringUtils.hasText(this.skipPattern) ? Pattern.compile(this.skipPattern)
				: TraceFilter.DEFAULT_SKIP_PATTERN;
		TraceFilter filter = new TraceFilter(this.traceManager, pattern, random);
		filter.setApplicationEventPublisher(publisher);
		return filter;
	}

	@Bean
	public FilterRegistrationBean traceWebFilter(TraceFilter filter) {
		return new FilterRegistrationBean(filter);
	}
}
