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

import net.ttddyy.dsproxy.listener.QueryCountStrategy;
import net.ttddyy.dsproxy.listener.QueryExecutionListener;
import net.ttddyy.dsproxy.listener.SingleQueryCountHolder;
import net.ttddyy.dsproxy.proxy.GlobalConnectionIdManager;
import net.ttddyy.dsproxy.proxy.ResultSetProxyLogicFactory;
import net.ttddyy.dsproxy.proxy.SimpleResultSetProxyLogicFactory;
import net.ttddyy.dsproxy.support.ProxyDataSource;
import net.ttddyy.dsproxy.transform.ParameterTransformer;
import net.ttddyy.dsproxy.transform.QueryTransformer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.instrument.jdbc.TraceDataSourceNameResolver;
import org.springframework.cloud.sleuth.instrument.jdbc.TraceQueryExecutionListener;
import org.springframework.context.annotation.Bean;

/**
 * Configuration for integration with datasource-proxy, allows to use define custom
 * {@link QueryExecutionListener}, {@link ParameterTransformer} and
 * {@link QueryTransformer}.
 *
 * @author Arthur Gavlyukovskiy
 */
@ConditionalOnClass(ProxyDataSource.class)
class DataSourceProxyConfiguration {

	@Autowired
	private TraceDataSourceDecoratorProperties dataSourceDecoratorProperties;

	@Bean
	@ConditionalOnMissingBean
	DataSourceProxyConnectionIdManagerProvider connectionIdManagerProvider() {
		return GlobalConnectionIdManager::new;
	}

	@Bean
	DataSourceProxyBuilderConfigurer proxyDataSourceBuilderConfigurer() {
		return new DataSourceProxyBuilderConfigurer();
	}

	@Bean
	TraceDataSourceProxyTraceDataSourceDecorator proxyDataSourceDecorator(
			DataSourceProxyBuilderConfigurer dataSourceProxyBuilderConfigurer,
			TraceDataSourceNameResolver dataSourceNameResolver) {
		return new TraceDataSourceProxyTraceDataSourceDecorator(dataSourceDecoratorProperties,
				dataSourceProxyBuilderConfigurer, dataSourceNameResolver);
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(value = "spring.sleuth.jdbc.decorator.datasource.datasource-proxy.count-query",
			havingValue = "true")
	public QueryCountStrategy queryCountStrategy() {
		return new SingleQueryCountHolder();
	}

	@Bean
	public TraceQueryExecutionListener traceQueryExecutionListener(Tracer tracer) {
		return new TraceQueryExecutionListener(tracer, dataSourceDecoratorProperties.getSleuth().getInclude());
	}

	@Bean
	@ConditionalOnMissingBean
	public ResultSetProxyLogicFactory traceResultSetProxyLogicFactory() {
		return new SimpleResultSetProxyLogicFactory();
	}

}
