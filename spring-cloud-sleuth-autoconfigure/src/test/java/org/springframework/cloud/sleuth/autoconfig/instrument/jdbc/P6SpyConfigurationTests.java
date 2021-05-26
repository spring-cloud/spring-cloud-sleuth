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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import javax.sql.DataSource;

import com.p6spy.engine.common.ConnectionInformation;
import com.p6spy.engine.common.P6LogQuery;
import com.p6spy.engine.event.CompoundJdbcEventListener;
import com.p6spy.engine.event.JdbcEventListener;
import com.p6spy.engine.logging.Category;
import com.p6spy.engine.logging.LoggingEventListener;
import com.p6spy.engine.spy.JdbcEventListenerFactory;
import com.p6spy.engine.spy.P6DataSource;
import com.p6spy.engine.spy.appender.CustomLineFormat;
import com.p6spy.engine.spy.appender.FormattedLogger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.autoconfig.brave.BraveAutoConfiguration;
import org.springframework.cloud.sleuth.instrument.jdbc.DataSourceWrapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class P6SpyConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class,
					TraceDataSourceDecoratorAutoConfiguration.class, BraveAutoConfiguration.class,
					TestSpanHandlerConfiguration.class, PropertyPlaceholderAutoConfiguration.class))
			.withPropertyValues("spring.datasource.initialization-mode=never",
					"spring.datasource.url:jdbc:h2:mem:testdb-" + ThreadLocalRandom.current().nextInt())
			.withClassLoader(new FilteredClassLoader("net.ttddyy.dsproxy"));

	@BeforeEach
	@AfterEach
	void resetLogAccumulator() {
		LogAccumulator.reset();
	}

	@Test
	void testCustomListeners() {
		ApplicationContextRunner contextRunner = this.contextRunner
				.withUserConfiguration(CustomListenerConfiguration.class);

		contextRunner.run(context -> {
			DataSource dataSource = context.getBean(DataSource.class);
			JdbcEventListenerFactory jdbcEventListenerFactory = context.getBean(JdbcEventListenerFactory.class);
			GetCountingListener getCountingListener = context.getBean(GetCountingListener.class);
			ClosingCountingListener closingCountingListener = context.getBean(ClosingCountingListener.class);
			P6DataSource p6DataSource = (P6DataSource) ((DataSourceWrapper) dataSource).getDecoratedDataSource();
			assertThat(p6DataSource).extracting("jdbcEventListenerFactory").isEqualTo(jdbcEventListenerFactory);

			CompoundJdbcEventListener jdbcEventListener = (CompoundJdbcEventListener) jdbcEventListenerFactory
					.createJdbcEventListener();

			assertThat(jdbcEventListener.getEventListeners()).contains(getCountingListener, closingCountingListener);
			assertThat(getCountingListener.connectionCount).isEqualTo(0);

			Connection connection1 = p6DataSource.getConnection();

			assertThat(getCountingListener.connectionCount).isEqualTo(1);
			assertThat(closingCountingListener.connectionCount).isEqualTo(0);

			Connection connection2 = p6DataSource.getConnection();

			assertThat(getCountingListener.connectionCount).isEqualTo(2);

			// order matters!
			connection2.close();

			assertThat(closingCountingListener.connectionCount).isEqualTo(1);

			// order matters!
			connection1.close();

			assertThat(closingCountingListener.connectionCount).isEqualTo(2);
			assertThat(context.getBean(Tracer.class).currentSpan()).isNull();
		});
	}

	@Test
	void testDoesNotRegisterLoggingListenerIfDisabled() {
		ApplicationContextRunner contextRunner = this.contextRunner
				.withPropertyValues("spring.sleuth.jdbc.decorator.datasource.p6spy.enable-logging:false");

		contextRunner.run(context -> {
			JdbcEventListenerFactory jdbcEventListenerFactory = context.getBean(JdbcEventListenerFactory.class);
			CompoundJdbcEventListener jdbcEventListener = (CompoundJdbcEventListener) jdbcEventListenerFactory
					.createJdbcEventListener();

			assertThat(jdbcEventListener.getEventListeners()).extracting("class")
					.doesNotContain(LoggingEventListener.class);
		});
	}

	@Test
	void testCanSetCustomLoggingFormat() {
		ApplicationContextRunner contextRunner = this.contextRunner
				.withPropertyValues("spring.sleuth.jdbc.decorator.datasource.p6spy.log-format:test %{connectionId}");

		contextRunner.run(context -> {
			JdbcEventListenerFactory jdbcEventListenerFactory = context.getBean(JdbcEventListenerFactory.class);
			CompoundJdbcEventListener jdbcEventListener = (CompoundJdbcEventListener) jdbcEventListenerFactory
					.createJdbcEventListener();

			assertThat(jdbcEventListener.getEventListeners()).extracting("class").contains(LoggingEventListener.class);
			assertThat(P6LogQuery.getLogger()).extracting("strategy").extracting("class")
					.isEqualTo(CustomLineFormat.class);
		});
	}

	@Test
	void testMultilineShouldNotOverrideCustomProperties() {
		System.setProperty("p6spy.config.logMessageFormat", "com.p6spy.engine.spy.appender.CustomLineFormat");
		System.setProperty("p6spy.config.excludecategories", "debug");
		ApplicationContextRunner contextRunner = this.contextRunner
				.withPropertyValues("spring.sleuth.jdbc.decorator.datasource.p6spy.multiline:true");

		contextRunner.run(context -> {
			JdbcEventListenerFactory jdbcEventListenerFactory = context.getBean(JdbcEventListenerFactory.class);
			CompoundJdbcEventListener jdbcEventListener = (CompoundJdbcEventListener) jdbcEventListenerFactory
					.createJdbcEventListener();

			assertThat(jdbcEventListener.getEventListeners()).extracting("class").contains(LoggingEventListener.class);
			assertThat(P6LogQuery.getLogger()).extracting("strategy").extracting("class")
					.isEqualTo(CustomLineFormat.class);
		});
	}

	@Test
	void testUseCustomLogger() {
		ApplicationContextRunner contextRunner = this.contextRunner.withPropertyValues(
				"spring.sleuth.jdbc.decorator.datasource.p6spy.logging:custom",
				"spring.sleuth.jdbc.decorator.datasource.p6spy.custom-appender-class:"
						+ LogAccumulator.class.getName());

		contextRunner.run(context -> {
			DataSource dataSource = context.getBean(DataSource.class);
			dataSource.getConnection().close();

			assertThat(P6LogQuery.getLogger()).isInstanceOf(LogAccumulator.class);
		});
	}

	@Test
	void testLogFilterPattern() {
		ApplicationContextRunner contextRunner = this.contextRunner.withPropertyValues(
				"spring.sleuth.jdbc.decorator.datasource.p6spy.logging:custom",
				"spring.sleuth.jdbc.decorator.datasource.p6spy.custom-appender-class:" + LogAccumulator.class.getName(),
				"spring.sleuth.jdbc.decorator.datasource.p6spy.log-filter.pattern:.*table1.*");

		contextRunner.run(context -> {
			DataSource dataSource = context.getBean(DataSource.class);
			try (Connection connection = dataSource.getConnection();
					PreparedStatement ps1 = connection.prepareStatement("select 1 /* from table1 */");
					PreparedStatement ps2 = connection.prepareStatement("select 1 /* from table2 */")) {
				ps1.execute();
				ps2.execute();
			}

			assertThat(LogAccumulator.MESSAGES).hasSize(1);
			assertThat(LogAccumulator.MESSAGES).allMatch(message -> message.contains("table1"));
		});
	}

	@Test
	void testLogFilterPatternMatchAll() {
		ApplicationContextRunner contextRunner = this.contextRunner.withPropertyValues(
				"spring.sleuth.jdbc.decorator.datasource.p6spy.logging:custom",
				"spring.sleuth.jdbc.decorator.datasource.p6spy.custom-appender-class:" + LogAccumulator.class.getName(),
				"spring.sleuth.jdbc.decorator.datasource.p6spy.log-filter.pattern:.*");

		contextRunner.run(context -> {
			DataSource dataSource = context.getBean(DataSource.class);
			try (Connection connection = dataSource.getConnection();
					PreparedStatement ps1 = connection.prepareStatement("select 1 /* from table1 */");
					PreparedStatement ps2 = connection.prepareStatement("select 1 /* from table2 */")) {
				ps1.execute();
				ps2.execute();
			}

			assertThat(LogAccumulator.MESSAGES).hasSize(2);
		});
	}

	@Configuration
	static class CustomListenerConfiguration {

		@Bean
		public GetCountingListener wrappingCountingListener() {
			return new GetCountingListener();
		}

		@Bean
		public ClosingCountingListener closingCountingListener() {
			return new ClosingCountingListener();
		}

	}

	static class GetCountingListener extends JdbcEventListener {

		int connectionCount = 0;

		@Override
		public void onAfterGetConnection(ConnectionInformation connectionInformation, SQLException e) {
			connectionCount++;
		}

	}

	static class ClosingCountingListener extends JdbcEventListener {

		int connectionCount = 0;

		@Override
		public void onAfterConnectionClose(ConnectionInformation connectionInformation, SQLException e) {
			connectionCount++;
		}

	}

	public static class LogAccumulator extends FormattedLogger {

		static final List<String> MESSAGES = new ArrayList<>();
		static final List<Exception> EXCEPTIONS = new ArrayList<>();

		public static void reset() {
			MESSAGES.clear();
			EXCEPTIONS.clear();
		}

		@Override
		public void logException(Exception e) {
			EXCEPTIONS.add(e);
		}

		@Override
		public void logText(String text) {
			MESSAGES.add(text);
		}

		@Override
		public boolean isCategoryEnabled(Category category) {
			return true;
		}

	}

}
