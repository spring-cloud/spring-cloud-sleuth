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
package org.springframework.cloud.sleuth.correlation;

import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.embedded.FilterRegistrationBean;
import org.springframework.cloud.sleuth.resttemplate.SleuthRestTemplateAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.regex.Pattern;

/**
 * Registers beans that add correlation id to requests
 *
 * @see CorrelationIdAspect
 * @see CorrelationIdFilter
 *
 * @author Tomasz Nurkewicz, 4financeIT
 * @author Marcin Grzejszczak, 4financeIT
 * @author Michal Chmielarz, 4financeIT
 */
@Configuration
@ConditionalOnProperty(value = "spring.cloud.sleuth.correlation.enabled", matchIfMissing = true)
@AutoConfigureAfter(SleuthRestTemplateAutoConfiguration.class)
public class CorrelationIdAutoConfiguration {

	/**
	 * Pattern for URLs that should be skipped in correlationID setting
	 */
	@Value("${spring.cloud.sleuth.correlation.skipPattern:}")
	private String skipPattern;

	@Bean
	@ConditionalOnMissingBean
	public CorrelationIdAspect correlationIdAspect() {
		return new CorrelationIdAspect();
	}

	@Bean
	@ConditionalOnMissingBean
	public FilterRegistrationBean correlationHeaderFilter(UuidGenerator uuidGenerator) {
		Pattern pattern = StringUtils.isBlank(skipPattern) ? Pattern.compile(skipPattern) : CorrelationIdFilter.DEFAULT_SKIP_PATTERN;
		return new FilterRegistrationBean(new CorrelationIdFilter(uuidGenerator, pattern));
	}

	@Bean
	@ConditionalOnMissingBean
	public UuidGenerator uuidGenerator() {
		return new UuidGenerator();
	}

	@Bean
	public CorrelationIdSettingRestTemplateInterceptor correlationIdSettingRestTemplateInterceptor() {
		return new CorrelationIdSettingRestTemplateInterceptor();
	}
}
