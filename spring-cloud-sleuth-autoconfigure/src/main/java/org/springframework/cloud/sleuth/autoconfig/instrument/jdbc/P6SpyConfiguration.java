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

import java.util.ArrayList;
import java.util.List;

import javax.sql.CommonDataSource;

import com.p6spy.engine.event.JdbcEventListener;
import com.p6spy.engine.spy.DefaultJdbcEventListenerFactory;
import com.p6spy.engine.spy.JdbcEventListenerFactory;
import com.p6spy.engine.spy.P6DataSource;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.sleuth.instrument.jdbc.DataSourceNameResolver;
import org.springframework.cloud.sleuth.instrument.jdbc.P6SpyContextJdbcEventListenerFactory;
import org.springframework.cloud.sleuth.instrument.jdbc.P6SpyDataSourceDecorator;
import org.springframework.cloud.sleuth.instrument.jdbc.TraceJdbcEventListener;
import org.springframework.cloud.sleuth.instrument.jdbc.TraceListenerStrategySpanCustomizer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

/**
 * Configuration for integration with p6spy, allows to define custom
 * {@link JdbcEventListener}.
 *
 * @author Arthur Gavlyukovskiy
 */
@ConditionalOnClass(P6DataSource.class)
@ConditionalOnProperty(name = "spring.sleuth.jdbc.p6spy.enabled", havingValue = "true", matchIfMissing = true)
class P6SpyConfiguration {

	@Bean
	static P6SpyPropertiesSetter p6SpyPropertiesSetter(ConfigurableApplicationContext context) {
		return new P6SpyPropertiesSetter(context);
	}

	@Bean
	@ConditionalOnMissingBean
	JdbcEventListenerFactory traceJdbcEventListenerFactory(ObjectProvider<List<JdbcEventListener>> listeners) {
		JdbcEventListenerFactory jdbcEventListenerFactory = new DefaultJdbcEventListenerFactory();
		List<JdbcEventListener> listenerList = listeners.getIfAvailable();
		return listenerList != null ? new P6SpyContextJdbcEventListenerFactory(jdbcEventListenerFactory, listenerList)
				: jdbcEventListenerFactory;
	}

	@Bean
	P6SpyDataSourceDecorator p6SpyDataSourceDecorator(JdbcEventListenerFactory jdbcEventListenerFactory) {
		return new P6SpyDataSourceDecorator(jdbcEventListenerFactory);
	}

	@Bean
	TraceJdbcEventListener tracingJdbcEventListener(BeanFactory beanFactory,
			DataSourceNameResolver dataSourceNameResolver, TraceJdbcProperties traceJdbcProperties,
			ObjectProvider<List<TraceListenerStrategySpanCustomizer<? super CommonDataSource>>> customizers) {
		return new TraceJdbcEventListener(beanFactory, dataSourceNameResolver, traceJdbcProperties.getIncludes(),
				traceJdbcProperties.getP6spy().getTracing().isIncludeParameterValues(),
				customizers.getIfAvailable(ArrayList::new));
	}

}
