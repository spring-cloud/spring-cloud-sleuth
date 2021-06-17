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

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import javax.sql.DataSource;

import net.ttddyy.dsproxy.ExecutionInfo;
import net.ttddyy.dsproxy.QueryInfo;
import net.ttddyy.dsproxy.listener.ChainListener;
import net.ttddyy.dsproxy.listener.QueryExecutionListener;
import net.ttddyy.dsproxy.listener.logging.CommonsQueryLoggingListener;
import net.ttddyy.dsproxy.listener.logging.CommonsSlowQueryListener;
import net.ttddyy.dsproxy.listener.logging.JULQueryLoggingListener;
import net.ttddyy.dsproxy.listener.logging.JULSlowQueryListener;
import net.ttddyy.dsproxy.listener.logging.SLF4JLogLevel;
import net.ttddyy.dsproxy.listener.logging.SLF4JQueryLoggingListener;
import net.ttddyy.dsproxy.listener.logging.SLF4JSlowQueryListener;
import net.ttddyy.dsproxy.listener.logging.SystemOutQueryLoggingListener;
import net.ttddyy.dsproxy.listener.logging.SystemOutSlowQueryListener;
import net.ttddyy.dsproxy.proxy.DefaultConnectionIdManager;
import net.ttddyy.dsproxy.proxy.GlobalConnectionIdManager;
import net.ttddyy.dsproxy.support.ProxyDataSource;
import net.ttddyy.dsproxy.transform.ParameterTransformer;
import net.ttddyy.dsproxy.transform.QueryTransformer;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.sleuth.autoconfig.TraceNoOpAutoConfiguration;
import org.springframework.cloud.sleuth.instrument.jdbc.DataSourceProxyConnectionIdManagerProvider;
import org.springframework.cloud.sleuth.instrument.jdbc.DataSourceWrapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.filter;

class ProxyDataSourceConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withConfiguration(
					AutoConfigurations.of(DataSourceAutoConfiguration.class, TraceJdbcAutoConfiguration.class,
							TraceNoOpAutoConfiguration.class, PropertyPlaceholderAutoConfiguration.class))
			.withPropertyValues("spring.datasource.initialization-mode=never", "spring.sleuth.noop.enabled=true",
					"spring.datasource.url:jdbc:h2:mem:testdb-" + ThreadLocalRandom.current().nextInt())
			.withClassLoader(new FilteredClassLoader("com.p6spy"));

	@Test
	void testRegisterLogAndSlowQueryLogByDefaultToSlf4j() {
		contextRunner.run(context -> {
			DataSource dataSource = context.getBean(DataSource.class);
			ProxyDataSource proxyDataSource = (ProxyDataSource) ((DataSourceWrapper) dataSource)
					.getDecoratedDataSource();
			ChainListener chainListener = proxyDataSource.getProxyConfig().getQueryListener();
			assertThat(chainListener.getListeners()).extracting("class").contains(SLF4JSlowQueryListener.class);
			assertThat(chainListener.getListeners()).extracting("class").contains(SLF4JQueryLoggingListener.class);
		});
	}

	@Test
	void testRegisterLogAndSlowQueryLogByUsingSlf4j() {
		ApplicationContextRunner contextRunner = this.contextRunner
				.withPropertyValues("spring.sleuth.jdbc.datasource-proxy.logging=slf4j");

		contextRunner.run(context -> {
			DataSource dataSource = context.getBean(DataSource.class);
			ProxyDataSource proxyDataSource = (ProxyDataSource) ((DataSourceWrapper) dataSource)
					.getDecoratedDataSource();
			ChainListener chainListener = proxyDataSource.getProxyConfig().getQueryListener();
			assertThat(chainListener.getListeners()).extracting("class").contains(SLF4JSlowQueryListener.class);
			assertThat(chainListener.getListeners()).extracting("class").contains(SLF4JQueryLoggingListener.class);
		});
	}

	@Test
	void testRegisterLogAndSlowQueryLogUsingSystemOut() {
		ApplicationContextRunner contextRunner = this.contextRunner
				.withPropertyValues("spring.sleuth.jdbc.datasource-proxy.logging=sysout");

		contextRunner.run(context -> {
			DataSource dataSource = context.getBean(DataSource.class);
			ProxyDataSource proxyDataSource = (ProxyDataSource) ((DataSourceWrapper) dataSource)
					.getDecoratedDataSource();
			ChainListener chainListener = proxyDataSource.getProxyConfig().getQueryListener();
			assertThat(chainListener.getListeners()).extracting("class").contains(SystemOutSlowQueryListener.class);
			assertThat(chainListener.getListeners()).extracting("class").contains(SystemOutQueryLoggingListener.class);
		});
	}

	@Test
	void testRegisterLogAndSlowQueryLogUsingJUL() {
		ApplicationContextRunner contextRunner = this.contextRunner
				.withPropertyValues("spring.sleuth.jdbc.datasourceProxy.logging=jul");

		contextRunner.run(context -> {
			DataSource dataSource = context.getBean(DataSource.class);
			ProxyDataSource proxyDataSource = (ProxyDataSource) ((DataSourceWrapper) dataSource)
					.getDecoratedDataSource();
			ChainListener chainListener = proxyDataSource.getProxyConfig().getQueryListener();
			assertThat(chainListener.getListeners()).extracting("class").contains(JULSlowQueryListener.class);
			assertThat(chainListener.getListeners()).extracting("class").contains(JULQueryLoggingListener.class);
		});
	}

	@Test
	void testRegisterLogAndSlowQueryLogUsingApacheCommons() {
		ApplicationContextRunner contextRunner = this.contextRunner
				.withPropertyValues("spring.sleuth.jdbc.datasourceProxy.logging=commons");

		contextRunner.run(context -> {
			DataSource dataSource = context.getBean(DataSource.class);
			ProxyDataSource proxyDataSource = (ProxyDataSource) ((DataSourceWrapper) dataSource)
					.getDecoratedDataSource();
			ChainListener chainListener = proxyDataSource.getProxyConfig().getQueryListener();
			assertThat(chainListener.getListeners()).extracting("class").contains(CommonsSlowQueryListener.class);
			assertThat(chainListener.getListeners()).extracting("class").contains(CommonsQueryLoggingListener.class);
		});
	}

	@Test
	void testCustomParameterAndQueryTransformer() {
		ApplicationContextRunner contextRunner = this.contextRunner
				.withUserConfiguration(CustomDataSourceProxyConfiguration.class);

		contextRunner.run(context -> {
			DataSource dataSource = context.getBean(DataSource.class);
			ProxyDataSource proxyDataSource = (ProxyDataSource) ((DataSourceWrapper) dataSource)
					.getDecoratedDataSource();
			ParameterTransformer parameterTransformer = context.getBean(ParameterTransformer.class);
			QueryTransformer queryTransformer = context.getBean(QueryTransformer.class);
			assertThat(proxyDataSource.getProxyConfig().getParameterTransformer()).isSameAs(parameterTransformer);
			assertThat(proxyDataSource.getProxyConfig().getQueryTransformer()).isSameAs(queryTransformer);
		});
	}

	@Test
	void testCustomListeners() {
		ApplicationContextRunner contextRunner = this.contextRunner
				.withUserConfiguration(CustomListenerConfiguration.class);

		contextRunner.run(context -> {
			DataSource dataSource = context.getBean(DataSource.class);
			ProxyDataSource proxyDataSource = (ProxyDataSource) ((DataSourceWrapper) dataSource)
					.getDecoratedDataSource();
			QueryExecutionListener queryExecutionListener = context.getBean(QueryExecutionListener.class);

			ChainListener chainListener = proxyDataSource.getProxyConfig().getQueryListener();
			assertThat(chainListener.getListeners()).contains(queryExecutionListener);
		});
	}

	@Test
	void testGlobalConnectionIdManagerByDefault() {
		contextRunner.run(context -> {
			DataSource dataSource = context.getBean(DataSource.class);
			ProxyDataSource proxyDataSource = (ProxyDataSource) ((DataSourceWrapper) dataSource)
					.getDecoratedDataSource();

			assertThat(proxyDataSource.getConnectionIdManager()).isInstanceOf(GlobalConnectionIdManager.class);
		});
	}

	@Test
	void testCustomConnectionIdManager() {
		ApplicationContextRunner contextRunner = this.contextRunner
				.withUserConfiguration(CustomDataSourceProxyConfiguration.class);

		contextRunner.run(context -> {
			DataSource dataSource = context.getBean(DataSource.class);
			ProxyDataSource proxyDataSource = (ProxyDataSource) ((DataSourceWrapper) dataSource)
					.getDecoratedDataSource();

			assertThat(proxyDataSource.getConnectionIdManager()).isInstanceOf(DefaultConnectionIdManager.class);
		});
	}

	@Test
	void testLogLevelIsCustomizable() {
		ApplicationContextRunner contextRunner = this.contextRunner.withPropertyValues(
				"spring.sleuth.jdbc.datasource-proxy.logging=slf4j",
				"spring.sleuth.jdbc.datasource-proxy.query.log-level=INFO");

		contextRunner.run(context -> {
			DataSource dataSource = context.getBean(DataSource.class);
			ProxyDataSource proxyDataSource = (ProxyDataSource) ((DataSourceWrapper) dataSource)
					.getDecoratedDataSource();
			ChainListener chainListener = proxyDataSource.getProxyConfig().getQueryListener();
			assertThat(chainListener.getListeners()).extracting("class").contains(SLF4JQueryLoggingListener.class);
			assertThat(filter(chainListener.getListeners()).with("class").equalsTo(SLF4JQueryLoggingListener.class)
					.with("logLevel").equalsTo(SLF4JLogLevel.INFO).get()).hasSize(1);
		});
	}

	@Configuration
	static class CustomDataSourceProxyConfiguration {

		@Bean
		public ParameterTransformer parameterTransformer() {
			return (replacer, transformInfo) -> {
			};
		}

		@Bean
		public QueryTransformer queryTransformer() {
			return (transformInfo) -> "TestQuery";
		}

		@Bean
		public DataSourceProxyConnectionIdManagerProvider connectionIdManagerProvider() {
			return DefaultConnectionIdManager::new;
		}

	}

	@Configuration
	static class CustomListenerConfiguration {

		@Bean
		@Primary
		public QueryExecutionListener queryExecutionListener() {
			return new QueryExecutionListener() {
				@Override
				public void beforeQuery(ExecutionInfo execInfo, List<QueryInfo> queryInfoList) {
					System.out.println("beforeQuery");
				}

				@Override
				public void afterQuery(ExecutionInfo execInfo, List<QueryInfo> queryInfoList) {
					System.out.println("afterQuery");
				}
			};
		}

	}

}
