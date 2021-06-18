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

import net.ttddyy.dsproxy.listener.MethodExecutionListener;
import net.ttddyy.dsproxy.listener.QueryCountStrategy;
import net.ttddyy.dsproxy.listener.QueryExecutionListener;
import net.ttddyy.dsproxy.proxy.GlobalConnectionIdManager;
import net.ttddyy.dsproxy.proxy.ResultSetProxyLogicFactory;
import net.ttddyy.dsproxy.proxy.SimpleResultSetProxyLogicFactory;
import net.ttddyy.dsproxy.support.ProxyDataSource;
import net.ttddyy.dsproxy.transform.ParameterTransformer;
import net.ttddyy.dsproxy.transform.QueryTransformer;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.instrument.jdbc.DataSourceNameResolver;
import org.springframework.cloud.sleuth.instrument.jdbc.DataSourceProxyBuilderCustomizer;
import org.springframework.cloud.sleuth.instrument.jdbc.DataSourceProxyConnectionIdManagerProvider;
import org.springframework.cloud.sleuth.instrument.jdbc.DataSourceProxyDataSourceDecorator;
import org.springframework.cloud.sleuth.instrument.jdbc.DataSourceProxyProperties;
import org.springframework.cloud.sleuth.instrument.jdbc.TraceListenerStrategySpanCustomizer;
import org.springframework.cloud.sleuth.instrument.jdbc.TraceQueryExecutionListener;
import org.springframework.context.annotation.Bean;

/**
 * Configuration for integration with datasource-proxy, allows to use define custom
 * {@link QueryExecutionListener}, {@link ParameterTransformer} and
 * {@link QueryTransformer}.
 *
 * @author Arthur Gavlyukovskiy
 * @author Chintan Radia
 */
@ConditionalOnClass(ProxyDataSource.class)
@ConditionalOnProperty(name = "spring.sleuth.jdbc.datasource-proxy.enabled", havingValue = "true",
		matchIfMissing = true)
class DataSourceProxyConfiguration {

	@Bean
	@ConditionalOnMissingBean
	DataSourceProxyConnectionIdManagerProvider traceConnectionIdManagerProvider() {
		return GlobalConnectionIdManager::new;
	}

	@Bean
	DataSourceProxyBuilderCustomizer proxyDataSourceBuilderConfigurer(
			ObjectProvider<QueryCountStrategy> queryCountStrategy,
			ObjectProvider<List<QueryExecutionListener>> listeners,
			ObjectProvider<List<MethodExecutionListener>> methodExecutionListeners,
			ObjectProvider<ParameterTransformer> parameterTransformer,
			ObjectProvider<QueryTransformer> queryTransformer,
			ObjectProvider<ResultSetProxyLogicFactory> resultSetProxyLogicFactory,
			ObjectProvider<DataSourceProxyConnectionIdManagerProvider> dataSourceProxyConnectionIdManagerProvider,
			TraceJdbcProperties traceJdbcProperties) {
		return new DataSourceProxyBuilderCustomizer(queryCountStrategy.getIfAvailable(() -> null),
				listeners.getIfAvailable(() -> null), methodExecutionListeners.getIfAvailable(() -> null),
				parameterTransformer.getIfAvailable(() -> null), queryTransformer.getIfAvailable(() -> null),
				resultSetProxyLogicFactory.getIfAvailable(() -> null),
				dataSourceProxyConnectionIdManagerProvider.getIfAvailable(() -> null), props(traceJdbcProperties));
	}

	private DataSourceProxyProperties props(TraceJdbcProperties traceJdbcProperties) {
		TraceJdbcProperties.DataSourceProxyProperties originalProxy = traceJdbcProperties.getDatasourceProxy();
		DataSourceProxyProperties props = new DataSourceProxyProperties();
		BeanUtils.copyProperties(originalProxy, props);
		props.setLogging(DataSourceProxyProperties.DataSourceProxyLogging.valueOf(originalProxy.getLogging().name()));
		BeanUtils.copyProperties(originalProxy.getQuery(), props.getQuery());
		BeanUtils.copyProperties(originalProxy.getSlowQuery(), props.getSlowQuery());
		return props;
	}

	@Bean
	DataSourceProxyDataSourceDecorator proxyDataSourceDecorator(
			DataSourceProxyBuilderCustomizer dataSourceProxyBuilderCustomizer,
			DataSourceNameResolver dataSourceNameResolver) {
		return new DataSourceProxyDataSourceDecorator(dataSourceProxyBuilderCustomizer, dataSourceNameResolver);
	}

	@Bean
	TraceQueryExecutionListener traceQueryExecutionListener(Tracer tracer,
			TraceJdbcProperties dataSourceDecoratorProperties,
			ObjectProvider<List<TraceListenerStrategySpanCustomizer>> customizers) {
		return new TraceQueryExecutionListener(tracer, dataSourceDecoratorProperties.getIncludes(),
				customizers.getIfAvailable(ArrayList::new));
	}

	@Bean
	@ConditionalOnMissingBean
	ResultSetProxyLogicFactory traceResultSetProxyLogicFactory() {
		return new SimpleResultSetProxyLogicFactory();
	}

}
