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

package org.springframework.cloud.sleuth.zipkin;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.kristofa.brave.SpanCollector;
import com.github.kristofa.brave.zipkin.ZipkinSpanCollector;

/**
 * @author Spencer Gibb
 */
@Configuration
@EnableConfigurationProperties
@ConditionalOnClass(ZipkinSpanCollector.class)
@ConditionalOnProperty(value = "spring.sleuth.zipkin.enabled", matchIfMissing = true)
public class ZipkinAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean(SpanCollector.class)
	public ZipkinSpanCollector spanCollector() {
		return new ZipkinSpanCollector(zipkinProperties().getHost(), zipkinProperties()
				.getPort());
	}

	@Bean
	public ZipkinProperties zipkinProperties() {
		return new ZipkinProperties();
	}

	@Bean
	// @ConditionalOnProperty(value = "spring.sleuth.zipkin.braveTracer.enabled", havingValue = "false")
	public ZipkinSpanListener sleuthTracer(SpanCollector spanCollector) {
		return new ZipkinSpanListener(spanCollector);
	}

}
