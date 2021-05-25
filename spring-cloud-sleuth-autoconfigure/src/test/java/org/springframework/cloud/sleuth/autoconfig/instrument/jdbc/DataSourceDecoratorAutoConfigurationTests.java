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

import java.io.PrintWriter;
import java.sql.Connection;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

import javax.sql.DataSource;

import com.p6spy.engine.spy.P6DataSource;
import com.zaxxer.hikari.HikariDataSource;
import net.ttddyy.dsproxy.support.ProxyDataSource;
import org.apache.commons.dbcp2.BasicDataSource;
import org.junit.jupiter.api.Test;

import org.springframework.beans.DirectFieldAccessor;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.sleuth.autoconfig.brave.BraveAutoConfiguration;
import org.springframework.cloud.sleuth.instrument.jdbc.DataSourceDecorator;
import org.springframework.cloud.sleuth.instrument.jdbc.DataSourceWrapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class DataSourceDecoratorAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class,
					TraceDataSourceDecoratorAutoConfiguration.class, BraveAutoConfiguration.class,
					TestSpanHandlerConfiguration.class, PropertyPlaceholderAutoConfiguration.class))
			.withPropertyValues("spring.datasource.initialization-mode=never",
					"spring.datasource.url:jdbc:h2:mem:testdb-" + ThreadLocalRandom.current().nextInt());

	@Test
	void testDecoratingInDefaultOrder() {
		contextRunner.run(context -> {
			DataSource dataSource = context.getBean(DataSource.class);
			assertThat(dataSource).isInstanceOf(DataSourceWrapper.class);

			DataSourceWrapper DataSourceWrapper = (DataSourceWrapper) dataSource;
			assertThat(DataSourceWrapper.getDecoratedDataSource()).isInstanceOf(P6DataSource.class);
			P6DataSource p6DataSource = (P6DataSource) DataSourceWrapper.getDecoratedDataSource();

			DataSource p6WrappedDataSource = (DataSource) ReflectionTestUtils.getField(p6DataSource, "realDataSource");
			assertThat(p6WrappedDataSource).isInstanceOf(ProxyDataSource.class);
			ProxyDataSource proxyDataSource = (ProxyDataSource) p6WrappedDataSource;
		});
	}

	@Test
	void testNoDecoratingForExcludeBeans() {
		ApplicationContextRunner contextRunner = this.contextRunner
				.withPropertyValues("spring.sleuth.jdbc.decorator.datasource.excluded-beans:dataSource");

		contextRunner.run(context -> {
			DataSource dataSource = context.getBean(DataSource.class);

			assertThat(dataSource).isInstanceOf(HikariDataSource.class);
		});
	}

	@Test
	void testDecoratingWhenDefaultProxyProviderNotAvailable() {
		ApplicationContextRunner contextRunner = this.contextRunner;

		contextRunner.run(context -> {
			DataSource dataSource = context.getBean(DataSource.class);

			assertThat(((DataSourceWrapper) dataSource).getOriginalDataSource()).isInstanceOf(HikariDataSource.class);

			DataSourceWrapper DataSourceWrapper = (DataSourceWrapper) dataSource;
			assertThat(DataSourceWrapper.getDecoratedDataSource()).isInstanceOf(P6DataSource.class);
			P6DataSource p6DataSource = (P6DataSource) DataSourceWrapper.getDecoratedDataSource();

			DataSource p6WrappedDataSource = (DataSource) ReflectionTestUtils.getField(p6DataSource, "realDataSource");
			assertThat(p6WrappedDataSource).isInstanceOf(ProxyDataSource.class);
			ProxyDataSource proxyDataSource = (ProxyDataSource) p6WrappedDataSource;

			DataSource dsProxyWrappedDataSource = (DataSource) ReflectionTestUtils.getField(proxyDataSource,
					"dataSource");
			assertThat(dsProxyWrappedDataSource).isEqualTo(DataSourceWrapper.getOriginalDataSource());
		});
	}

	@Test
	void testDecoratedHikariSpecificPropertiesIsSet() {
		ApplicationContextRunner contextRunner = this.contextRunner.withPropertyValues(
				"spring.datasource.type:" + HikariDataSource.class.getName(),
				"spring.datasource.hikari.catalog:test_catalog");

		contextRunner.run(context -> {
			DataSource dataSource = context.getBean(DataSource.class);
			assertThat(dataSource).isNotNull();
			assertThat(dataSource).isInstanceOf(DataSourceWrapper.class);
			DataSource realDataSource = ((DataSourceWrapper) dataSource).getOriginalDataSource();
			assertThat(realDataSource).isInstanceOf(HikariDataSource.class);
			assertThat(((HikariDataSource) realDataSource).getCatalog()).isEqualTo("test_catalog");
		});
	}

	@Test
	void testCustomDataSourceIsDecorated() {
		ApplicationContextRunner contextRunner = this.contextRunner
				.withUserConfiguration(TestDataSourceConfiguration.class);

		contextRunner.run(context -> {
			DataSource dataSource = context.getBean(DataSource.class);
			assertThat(dataSource).isInstanceOf(DataSourceWrapper.class);
			DataSource realDataSource = ((DataSourceWrapper) dataSource).getOriginalDataSource();
			assertThat(realDataSource).isInstanceOf(BasicDataSource.class);
		});
	}

	@Test
	void testScopedDataSourceIsNotDecorated() {
		ApplicationContextRunner contextRunner = this.contextRunner
				.withUserConfiguration(TestScopedDataSourceConfiguration.class);

		contextRunner.run(context -> {
			assertThat(context).getBeanNames(DataSource.class).containsOnly("dataSource", "scopedTarget.dataSource");
			assertThat(context).getBean("dataSource").isInstanceOf(DataSourceWrapper.class);
			assertThat(context).getBean("scopedTarget.dataSource").isNotInstanceOf(DataSourceWrapper.class);
		});
	}

	@Test
	void testCustomDataSourceDecoratorApplied() {
		ApplicationContextRunner contextRunner = this.contextRunner
				.withUserConfiguration(TestDataSourceDecoratorConfiguration.class);

		contextRunner.run(context -> {
			DataSource dataSource = context.getBean(DataSource.class);
			assertThat(dataSource).isNotNull();

			DataSource customDataSource = ((DataSourceWrapper) dataSource).getDecoratedDataSource();
			assertThat(customDataSource).isInstanceOf(CustomDataSourceProxy.class);

			DataSource realDataSource = ((DataSourceWrapper) dataSource).getOriginalDataSource();
			assertThat(realDataSource).isInstanceOf(HikariDataSource.class);

			assertThat(dataSource).isInstanceOf(DataSourceWrapper.class);

			DataSourceWrapper DataSourceWrapper = (DataSourceWrapper) dataSource;

			assertThat(DataSourceWrapper.getDecoratedDataSource()).isInstanceOf(CustomDataSourceProxy.class);
			CustomDataSourceProxy customDataSourceProxy = (CustomDataSourceProxy) DataSourceWrapper
					.getDecoratedDataSource();

			assertThat(customDataSourceProxy.delegate).isInstanceOf(P6DataSource.class);
			P6DataSource p6DataSource = (P6DataSource) customDataSourceProxy.delegate;

			DataSource p6WrappedDataSource = (DataSource) ReflectionTestUtils.getField(p6DataSource, "realDataSource");
			assertThat(p6WrappedDataSource).isInstanceOf(ProxyDataSource.class);
			ProxyDataSource proxyDataSource = (ProxyDataSource) p6WrappedDataSource;
		});
	}

	@Test
	void testDecoratingCanBeDisabled() {
		ApplicationContextRunner contextRunner = this.contextRunner
				.withPropertyValues("spring.sleuth.jdbc.decorator.datasource.enabled:false");

		contextRunner.run(context -> {
			DataSource dataSource = context.getBean(DataSource.class);
			assertThat(dataSource).isInstanceOf(HikariDataSource.class);
		});
	}

	@Test
	void testDecoratingCanBeDisabledForSpecificBeans() {
		ApplicationContextRunner contextRunner = this.contextRunner
				.withPropertyValues("spring.sleuth.jdbc.decorator.datasource.excluded-beans:secondDataSource")
				.withUserConfiguration(TestMultiDataSourceConfiguration.class);

		contextRunner.run(context -> {
			DataSource dataSource = context.getBean("dataSource", DataSource.class);
			assertThat(dataSource).isInstanceOf(DataSourceWrapper.class);

			DataSource secondDataSource = context.getBean("secondDataSource", DataSource.class);
			assertThat(secondDataSource).isInstanceOf(BasicDataSource.class);
		});
	}

	@Test
	void testDecoratingChainBuiltCorrectly() {
		contextRunner.run(context -> {
			DataSource dataSource = context.getBean(DataSource.class);

			DataSourceWrapper dataSource1 = context.getBean(DataSourceWrapper.class);
			assertThat(dataSource1).isNotNull();

			DataSource p6DataSource = dataSource1.getDecoratedDataSource();
			assertThat(p6DataSource).isNotNull();
			assertThat(p6DataSource).isInstanceOf(P6DataSource.class);

			DataSource proxyDataSource = (DataSource) new DirectFieldAccessor(p6DataSource)
					.getPropertyValue("realDataSource");
			assertThat(proxyDataSource).isNotNull();
			assertThat(proxyDataSource).isInstanceOf(ProxyDataSource.class);
		});
	}

	@Test
	void testDecorateDynamicallyRegisteredBeans() {
		ApplicationContextRunner contextRunner = this.contextRunner.withInitializer(context -> {
			GenericApplicationContext gac = (GenericApplicationContext) context;
			gac.registerBean("ds1", DataSource.class, () -> new HikariDataSource());
			gac.registerBean("ds2", DataSource.class, () -> new HikariDataSource());
		});

		contextRunner.run(context -> {
			DataSource dataSource1 = context.getBean("ds1", DataSource.class);
			assertThat(dataSource1).isNotNull();
			assertThat(dataSource1).isInstanceOf(DataSourceWrapper.class);

			DataSource dataSource2 = context.getBean("ds2", DataSource.class);
			assertThat(dataSource2).isNotNull();
			assertThat(dataSource2).isInstanceOf(DataSourceWrapper.class);
		});
	}

	@Configuration
	static class TestDataSourceConfiguration {

		@Bean
		public DataSource dataSource() {
			BasicDataSource pool = new BasicDataSource();
			pool.setDriverClassName("org.hsqldb.jdbcDriver");
			pool.setUrl("jdbc:hsqldb:target/overridedb");
			pool.setUsername("sa");
			return pool;
		}

	}

	@Configuration
	static class TestDataSourceDecoratorConfiguration {

		@Bean
		public DataSourceDecorator customDataSourceDecorator() {
			return (beanName, dataSource) -> new CustomDataSourceProxy(dataSource);
		}

	}

	@Configuration
	static class TestMultiDataSourceConfiguration {

		@Bean
		@Primary
		public DataSource dataSource() {
			BasicDataSource pool = new BasicDataSource();
			pool.setDriverClassName("org.hsqldb.jdbcDriver");
			pool.setUrl("jdbc:hsqldb:target/db");
			pool.setUsername("sa");
			return pool;
		}

		@Bean
		public DataSource secondDataSource() {
			BasicDataSource pool = new BasicDataSource();
			pool.setDriverClassName("org.hsqldb.jdbcDriver");
			pool.setUrl("jdbc:hsqldb:target/db2");
			pool.setUsername("sa");
			return pool;
		}

	}

	@Configuration
	static class TestScopedDataSourceConfiguration {

		@Bean
		@Scope(value = "prototype", proxyMode = ScopedProxyMode.TARGET_CLASS)
		public DataSource dataSource() {
			BasicDataSource pool = new BasicDataSource();
			pool.setDriverClassName("org.hsqldb.jdbcDriver");
			pool.setUrl("jdbc:hsqldb:target/overridedb");
			pool.setUsername("sa");
			return pool;
		}

	}

	/**
	 * Custom proxy data source for tests.
	 *
	 * @author Arthur Gavlyukovskiy
	 */
	static class CustomDataSourceProxy implements DataSource {

		private final DataSource delegate;

		CustomDataSourceProxy(DataSource delegate) {
			this.delegate = delegate;
		}

		@Override
		public Connection getConnection() {
			return null;
		}

		@Override
		public Connection getConnection(String username, String password) {
			return null;
		}

		@Override
		public <T> T unwrap(Class<T> iface) {
			return null;
		}

		@Override
		public boolean isWrapperFor(Class<?> iface) {
			return false;
		}

		@Override
		public PrintWriter getLogWriter() {
			return null;
		}

		@Override
		public void setLogWriter(PrintWriter out) {

		}

		@Override
		public void setLoginTimeout(int seconds) {

		}

		@Override
		public int getLoginTimeout() {
			return 0;
		}

		@Override
		public Logger getParentLogger() {
			return null;
		}

	}

}
