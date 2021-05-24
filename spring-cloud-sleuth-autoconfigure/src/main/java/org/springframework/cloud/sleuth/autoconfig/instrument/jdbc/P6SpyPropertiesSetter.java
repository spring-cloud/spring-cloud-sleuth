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

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.p6spy.engine.logging.P6LogFactory;
import com.p6spy.engine.spy.P6ModuleManager;
import com.p6spy.engine.spy.P6SpyFactory;
import com.p6spy.engine.spy.option.EnvironmentVariables;
import com.p6spy.engine.spy.option.P6OptionsSource;
import com.p6spy.engine.spy.option.SpyDotProperties;
import com.p6spy.engine.spy.option.SystemProperties;
import org.slf4j.Logger;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.cloud.sleuth.instrument.jdbc.P6SpyProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Sets p6spy properties to / from system properties.
 *
 * @author Arthur Gavlyukovskiy
 * @since 3.1.0
 */
class P6SpyPropertiesSetter implements BeanDefinitionRegistryPostProcessor, Closeable {

	private static final Logger log = getLogger(P6SpyPropertiesSetter.class);

	private final ConfigurableApplicationContext context;

	private final Map<String, String> initialP6SpyOptions;

	P6SpyPropertiesSetter(ConfigurableApplicationContext context) {
		this.context = context;
		this.initialP6SpyOptions = findDefinedOptions();
	}

	@Override
	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
		ConfigurableEnvironment environment = this.context.getEnvironment();
		String customModuleList = initialP6SpyOptions.get("modulelist");
		boolean isEnableLogging = environment
				.getProperty("spring.sleuth.jdbc.decorator.datasource.p6spy.enable-logging", Boolean.class, true);
		if (customModuleList != null) {
			log.info("P6Spy modulelist is overridden, some p6spy configuration features will not be applied");
		}
		else {
			List<String> moduleList = new ArrayList<>();
			// default factory, holds P6Spy configuration
			moduleList.add(P6SpyFactory.class.getName());
			if (isEnableLogging) {
				moduleList.add(P6LogFactory.class.getName());
			}
			System.setProperty("p6spy.config.modulelist", String.join(",", moduleList));
		}
		if (!initialP6SpyOptions.containsKey("logMessageFormat")) {
			String logFormat = logFormat(environment);
			boolean isMultiline = multiLine(environment);
			if (logFormat != null) {
				System.setProperty("p6spy.config.logMessageFormat", "com.p6spy.engine.spy.appender.CustomLineFormat");
				System.setProperty("p6spy.config.customLogMessageFormat", logFormat);
			}
			else if (isMultiline) {
				System.setProperty("p6spy.config.logMessageFormat", "com.p6spy.engine.spy.appender.MultiLineFormat");
			}
		}
		if (isEnableLogging && !initialP6SpyOptions.containsKey("appender")) {
			P6SpyProperties.P6SpyLogging logging = P6SpyProperties.P6SpyLogging
					.valueOf(environment.getProperty("spring.sleuth.jdbc.decorator.datasource.p6spy.logging",
							String.class, P6SpyProperties.P6SpyLogging.SLF4J.toString()).toUpperCase());
			switch (logging) {
			case SYSOUT:
				System.setProperty("p6spy.config.appender", "com.p6spy.engine.spy.appender.StdoutLogger");
				break;
			case SLF4J:
				System.setProperty("p6spy.config.appender", "com.p6spy.engine.spy.appender.Slf4JLogger");
				break;
			case FILE:
				System.setProperty("p6spy.config.appender", "com.p6spy.engine.spy.appender.FileLogger");
				break;
			case CUSTOM:
				String customAppender = environment.getProperty(
						"spring.sleuth.jdbc.decorator.datasource.p6spy.custom-appender-class", String.class, "");
				System.setProperty("p6spy.config.appender", customAppender);
				break;
			}
		}
		if (!initialP6SpyOptions.containsKey("logfile")) {
			String logFile = environment.getProperty("spring.sleuth.jdbc.decorator.datasource.p6spy.log-file",
					String.class, "spy.log");
			System.setProperty("p6spy.config.logfile", logFile);
		}
		String pattern = environment.getProperty("spring.sleuth.jdbc.decorator.datasource.p6spy.log-filter.pattern",
				String.class);
		if (pattern != null) {
			System.setProperty("p6spy.config.filter", "true");
			System.setProperty("p6spy.config.sqlexpression", pattern);
		}
		// If factories were loaded before this method is initialized changing properties
		// will not be applied
		// Changes done in this method could not override anything user specified,
		// therefore it is safe to call reload
		P6ModuleManager.getInstance().reload();
	}

	private Boolean multiLine(ConfigurableEnvironment environment) {
		return environment.getProperty("spring.sleuth.jdbc.decorator.datasource.p6spy.multiline", Boolean.class, true);
	}

	private String logFormat(ConfigurableEnvironment environment) {
		return environment.getProperty("spring.sleuth.jdbc.decorator.datasource.p6spy.log-format", String.class);
	}

	@Override
	public void close() throws IOException {
		if (!initialP6SpyOptions.containsKey("modulelist")) {
			System.clearProperty("p6spy.config.modulelist");
		}
		if (!initialP6SpyOptions.containsKey("logMessageFormat")) {
			ConfigurableEnvironment environment = this.context.getEnvironment();
			String logFormat = logFormat(environment);
			boolean isMultiline = multiLine(environment);
			if (logFormat != null) {
				System.clearProperty("p6spy.config.logMessageFormat");
				System.clearProperty("p6spy.config.customLogMessageFormat");
			}
			else if (isMultiline) {
				System.clearProperty("p6spy.config.logMessageFormat");
			}
		}
		if (!initialP6SpyOptions.containsKey("appender")) {
			System.clearProperty("p6spy.config.appender");
		}
		if (!initialP6SpyOptions.containsKey("logfile")) {
			System.clearProperty("p6spy.config.logfile");
		}
		P6ModuleManager.getInstance().reload();
	}

	private Map<String, String> findDefinedOptions() {
		SpyDotProperties spyDotProperties = null;
		try {
			spyDotProperties = new SpyDotProperties();
		}
		catch (IOException ignored) {
		}
		return Stream.of(spyDotProperties, new EnvironmentVariables(), new SystemProperties()).filter(Objects::nonNull)
				.map(P6OptionsSource::getOptions).filter(Objects::nonNull)
				.flatMap(options -> options.entrySet().stream())
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
						// always using value from the first P6OptionsSource
						(value1, value2) -> value1));
	}

	@Override
	public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {

	}

}
