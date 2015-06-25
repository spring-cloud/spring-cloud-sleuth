/*
 * Copyright 2012-2015 the original author or authors.
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

import java.util.regex.Pattern;

import org.aspectj.lang.ProceedingJoinPoint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.embedded.FilterRegistrationBean;
import org.springframework.cloud.sleuth.Trace;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
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
@EnableAspectJAutoProxy
@ConditionalOnProperty(value = "spring.cloud.sleuth.trace.web.enabled", matchIfMissing = true)
@ConditionalOnWebApplication
@ConditionalOnClass(ProceedingJoinPoint.class)
public class TraceWebAutoConfiguration {

	/**
	 * Pattern for URLs that should be skipped in correlationID setting
	 */
	@Value("${spring.cloud.sleuth.trace.web.skipPattern:}")
	private String skipPattern;

	@Autowired
	private Trace trace;

	@Bean
	@ConditionalOnMissingBean
	public TraceWebAspect traceWebAspect() {
		return new TraceWebAspect(trace);
	}

	@Bean
	@ConditionalOnMissingBean
	public FilterRegistrationBean correlationHeaderFilter() {
		Pattern pattern = StringUtils.hasText(skipPattern) ? Pattern.compile(skipPattern)
				: TraceFilter.DEFAULT_SKIP_PATTERN;
		return new FilterRegistrationBean(new TraceFilter(trace, pattern));
	}
}
