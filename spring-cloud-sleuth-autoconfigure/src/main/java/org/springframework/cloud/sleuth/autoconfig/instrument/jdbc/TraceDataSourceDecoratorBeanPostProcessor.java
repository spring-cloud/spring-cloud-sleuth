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

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.aop.scope.ScopedProxyUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.cloud.sleuth.instrument.jdbc.DataSourceNameResolver;
import org.springframework.cloud.sleuth.instrument.jdbc.DataSourceWrapper;
import org.springframework.cloud.sleuth.instrument.jdbc.TraceDataSourceDecorator;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.util.ClassUtils;

/**
 * {@link BeanPostProcessor} that wraps all data source beans in {@link DataSource}
 * proxies specified in property 'spring.datasource.type'.
 *
 * @author Arthur Gavlyukovskiy
 * @since 3.1.0
 */
public class TraceDataSourceDecoratorBeanPostProcessor implements BeanPostProcessor, Ordered, ApplicationContextAware {

	private static final Log log = LogFactory.getLog(TraceDataSourceDecoratorBeanPostProcessor.class);

	private final static boolean HIKARI_AVAILABLE = ClassUtils.isPresent("com.zaxxer.hikari.HikariDataSource",
			DataSourceNameResolver.class.getClassLoader());

	private ApplicationContext applicationContext;

	private DataSourceNameResolver dataSourceNameResolver;

	private final Collection<String> excludedBeans;

	public TraceDataSourceDecoratorBeanPostProcessor(Collection<String> excludedBeans) {
		this.excludedBeans = excludedBeans;
	}

	@Override
	public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
		return bean;
	}

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
		if (bean instanceof DataSource && !ScopedProxyUtils.isScopedTarget(beanName)
				&& !this.excludedBeans.contains(beanName)) {
			Map<String, TraceDataSourceDecorator> decorators = this.applicationContext
					.getBeansOfType(TraceDataSourceDecorator.class).entrySet().stream()
					.sorted(Entry.comparingByValue(AnnotationAwareOrderComparator.INSTANCE))
					.collect(Collectors.toMap(Entry::getKey, Entry::getValue, (v1, v2) -> v2, LinkedHashMap::new));
			return decorate((DataSource) bean, getDataSourceName(bean, beanName), decorators);
		}
		else {
			return bean;
		}
	}

	private String getDataSourceName(Object bean, String beanName) {
		if (HIKARI_AVAILABLE && bean instanceof HikariDataSource) {
			HikariDataSource hikariDataSource = (HikariDataSource) bean;
			if (hikariDataSource.getPoolName() != null && !hikariDataSource.getPoolName().startsWith("HikariPool-")) {
				return hikariDataSource.getPoolName();
			}
		}
		return beanName;
	}

	private DataSource decorate(DataSource dataSource, String name, Map<String, TraceDataSourceDecorator> decorators) {
		getDataSourceNameResolver().addDataSource(name, dataSource);
		DataSource decoratedDataSource = dataSource;
		for (Entry<String, TraceDataSourceDecorator> decoratorEntry : decorators.entrySet()) {
			String decoratorBeanName = decoratorEntry.getKey();
			TraceDataSourceDecorator decorator = decoratorEntry.getValue();
			DataSource dataSourceBeforeDecorating = decoratedDataSource;
			decoratedDataSource = Objects.requireNonNull(decorator.decorate(name, decoratedDataSource),
					"DataSourceDecorator (" + decoratorBeanName + ", " + decorator + ") should not return null");
			if (dataSourceBeforeDecorating != decoratedDataSource) {
				getDataSourceNameResolver().addDataSource(name, decoratedDataSource);
			}
		}
		if (dataSource != decoratedDataSource) {
			if (log.isDebugEnabled()) {
				log.debug("The decorated data source [" + decoratedDataSource + "] will replace the original one ["
						+ dataSource + "]");
			}
			decoratedDataSource = new DataSourceWrapper(dataSource, decoratedDataSource);
			getDataSourceNameResolver().addDataSource(name, decoratedDataSource);
		}
		return decoratedDataSource;
	}

	private DataSourceNameResolver getDataSourceNameResolver() {
		if (this.dataSourceNameResolver == null) {
			this.dataSourceNameResolver = this.applicationContext.getBean(DataSourceNameResolver.class);
		}
		return this.dataSourceNameResolver;
	}

	@Override
	public int getOrder() {
		return Ordered.LOWEST_PRECEDENCE - 10;
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}

}
