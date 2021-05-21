/*
 * Copyright 2013-2021 the original author or authors.
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

package org.springframework.cloud.sleuth.autoconfig.instrument.jdbc;

import javax.sql.DataSource;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.instrument.jdbc.TraceDataSourceDecorator;
import org.springframework.cloud.sleuth.instrument.jdbc.TraceDataSourceNameResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * {@link EnableAutoConfiguration Auto-configuration} for proxying DataSource.
 *
 * @author Arthur Gavlyukovskiy
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TraceDataSourceDecoratorProperties.class)
@ConditionalOnProperty(name = "spring.sleuth.jdbc.decorator.datasource.enabled", havingValue = "true",
		matchIfMissing = true)
@ConditionalOnBean({ DataSource.class, Tracer.class })
@AutoConfigureAfter(DataSourceAutoConfiguration.class)
@Import({ P6SpyConfiguration.class, DataSourceProxyConfiguration.class })
public class TraceDataSourceDecoratorAutoConfiguration {

	@Bean
	@ConditionalOnBean(TraceDataSourceDecorator.class)
	static TraceDataSourceDecoratorBeanPostProcessor dataSourceDecoratorBeanPostProcessor(
			TraceDataSourceDecoratorProperties dataSourceDecoratorProperties) {
		return new TraceDataSourceDecoratorBeanPostProcessor(dataSourceDecoratorProperties.getExcludeBeans());
	}

	@Bean
	@ConditionalOnMissingBean
	TraceDataSourceNameResolver dataSourceNameResolver() {
		return new TraceDataSourceNameResolver();
	}

}
