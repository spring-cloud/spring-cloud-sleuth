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

package org.springframework.cloud.sleuth.instrument.jdbc;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;

import net.ttddyy.dsproxy.listener.MethodExecutionListener;
import net.ttddyy.dsproxy.listener.QueryCountStrategy;
import net.ttddyy.dsproxy.listener.QueryExecutionListener;
import net.ttddyy.dsproxy.listener.logging.CommonsLogLevel;
import net.ttddyy.dsproxy.listener.logging.SLF4JLogLevel;
import net.ttddyy.dsproxy.proxy.ResultSetProxyLogicFactory;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import net.ttddyy.dsproxy.transform.ParameterTransformer;
import net.ttddyy.dsproxy.transform.QueryTransformer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.lang.Nullable;

/**
 * Configurer for {@link ProxyDataSourceBuilder} based on the application context.
 *
 * @author Arthur Gavlyukovskiy
 * @since 3.1.0
 * @see ProxyDataSourceBuilder
 */
public class DataSourceProxyBuilderCustomizer {

	private static final Log log = LogFactory.getLog(DataSourceProxyBuilderCustomizer.class);

	private final QueryCountStrategy queryCountStrategy;

	private final List<QueryExecutionListener> listeners;

	private final List<MethodExecutionListener> methodExecutionListeners;

	private final ParameterTransformer parameterTransformer;

	private final QueryTransformer queryTransformer;

	private final ResultSetProxyLogicFactory resultSetProxyLogicFactory;

	private final DataSourceProxyConnectionIdManagerProvider dataSourceProxyConnectionIdManagerProvider;

	private final DataSourceProxyProperties datasourceProxy;

	public DataSourceProxyBuilderCustomizer(@Nullable QueryCountStrategy queryCountStrategy,
			@Nullable List<QueryExecutionListener> listeners,
			@Nullable List<MethodExecutionListener> methodExecutionListeners,
			@Nullable ParameterTransformer parameterTransformer, @Nullable QueryTransformer queryTransformer,
			@Nullable ResultSetProxyLogicFactory resultSetProxyLogicFactory,
			@Nullable DataSourceProxyConnectionIdManagerProvider dataSourceProxyConnectionIdManagerProvider,
			DataSourceProxyProperties datasourceProxy) {
		this.queryCountStrategy = queryCountStrategy;
		this.listeners = listeners;
		this.methodExecutionListeners = methodExecutionListeners;
		this.parameterTransformer = parameterTransformer;
		this.queryTransformer = queryTransformer;
		this.resultSetProxyLogicFactory = resultSetProxyLogicFactory;
		this.dataSourceProxyConnectionIdManagerProvider = dataSourceProxyConnectionIdManagerProvider;
		this.datasourceProxy = datasourceProxy;
	}

	public ProxyDataSourceBuilder customize(ProxyDataSourceBuilder proxyDataSourceBuilder) {
		switch (this.datasourceProxy.getLogging()) {
		case SLF4J:
			if (this.datasourceProxy.getQuery().isEnableLogging()) {
				proxyDataSourceBuilder.logQueryBySlf4j(toSlf4JLogLevel(this.datasourceProxy.getQuery().getLogLevel()),
						this.datasourceProxy.getQuery().getLoggerName());
			}
			if (this.datasourceProxy.getSlowQuery().isEnableLogging()) {
				proxyDataSourceBuilder.logSlowQueryBySlf4j(this.datasourceProxy.getSlowQuery().getThreshold(),
						TimeUnit.SECONDS, toSlf4JLogLevel(this.datasourceProxy.getSlowQuery().getLogLevel()),
						datasourceProxy.getSlowQuery().getLoggerName());
			}
			break;
		case JUL:
			if (this.datasourceProxy.getQuery().isEnableLogging()) {
				proxyDataSourceBuilder.logQueryByJUL(toJULLogLevel(this.datasourceProxy.getQuery().getLogLevel()),
						this.datasourceProxy.getQuery().getLoggerName());
			}
			if (this.datasourceProxy.getSlowQuery().isEnableLogging()) {
				proxyDataSourceBuilder.logSlowQueryByJUL(this.datasourceProxy.getSlowQuery().getThreshold(),
						TimeUnit.SECONDS, toJULLogLevel(this.datasourceProxy.getSlowQuery().getLogLevel()),
						this.datasourceProxy.getSlowQuery().getLoggerName());
			}
			break;
		case COMMONS:
			if (this.datasourceProxy.getQuery().isEnableLogging()) {
				proxyDataSourceBuilder.logQueryByCommons(
						toCommonsLogLevel(this.datasourceProxy.getQuery().getLogLevel()),
						datasourceProxy.getQuery().getLoggerName());
			}
			if (this.datasourceProxy.getSlowQuery().isEnableLogging()) {
				proxyDataSourceBuilder.logSlowQueryByCommons(this.datasourceProxy.getSlowQuery().getThreshold(),
						TimeUnit.SECONDS, toCommonsLogLevel(this.datasourceProxy.getSlowQuery().getLogLevel()),
						this.datasourceProxy.getSlowQuery().getLoggerName());
			}
			break;
		case SYSOUT:
			if (this.datasourceProxy.getQuery().isEnableLogging()) {
				proxyDataSourceBuilder.logQueryToSysOut();
			}
			if (this.datasourceProxy.getSlowQuery().isEnableLogging()) {
				proxyDataSourceBuilder.logSlowQueryToSysOut(this.datasourceProxy.getSlowQuery().getThreshold(),
						TimeUnit.SECONDS);
			}
			break;
		}
		if (this.datasourceProxy.isMultiline() && this.datasourceProxy.isJsonFormat()) {
			log.warn(
					"Found opposite multiline and json format, multiline will be used (may depend on library version)");
		}
		if (this.datasourceProxy.isMultiline()) {
			proxyDataSourceBuilder.multiline();
		}
		if (this.datasourceProxy.isJsonFormat()) {
			proxyDataSourceBuilder.asJson();
		}
		ifAvailable(this.listeners, l -> l.forEach(proxyDataSourceBuilder::listener));
		ifAvailable(this.methodExecutionListeners, m -> m.forEach(proxyDataSourceBuilder::methodListener));
		ifAvailable(this.parameterTransformer, proxyDataSourceBuilder::parameterTransformer);
		ifAvailable(this.queryTransformer, proxyDataSourceBuilder::queryTransformer);
		ifAvailable(this.resultSetProxyLogicFactory, proxyDataSourceBuilder::proxyResultSet);
		ifAvailable(this.dataSourceProxyConnectionIdManagerProvider,
				d -> proxyDataSourceBuilder.connectionIdManager(d.get()));
		return proxyDataSourceBuilder;
	}

	private <T> void ifAvailable(@Nullable T o, Consumer<T> consumer) {
		if (o != null) {
			consumer.accept(o);
		}
	}

	private SLF4JLogLevel toSlf4JLogLevel(String logLevel) {
		if (logLevel == null) {
			return null;
		}
		for (SLF4JLogLevel slf4JLogLevel : SLF4JLogLevel.values()) {
			if (slf4JLogLevel.name().equalsIgnoreCase(logLevel)) {
				return slf4JLogLevel;
			}
		}
		throw new IllegalArgumentException("Unresolved log level " + logLevel + " for slf4j logger, known levels: "
				+ Arrays.toString(SLF4JLogLevel.values()));
	}

	private Level toJULLogLevel(String logLevel) {
		if (logLevel == null) {
			return null;
		}
		try {
			return Level.parse(logLevel);
		}
		catch (IllegalArgumentException e) {
			if (logLevel.equalsIgnoreCase("DEBUG")) {
				return Level.FINE;
			}
			if (logLevel.equalsIgnoreCase("WARN")) {
				return Level.WARNING;
			}
			throw new IllegalArgumentException("Unresolved log level " + logLevel + " for java.util.logging", e);
		}
	}

	private CommonsLogLevel toCommonsLogLevel(String logLevel) {
		if (logLevel == null) {
			return null;
		}
		for (CommonsLogLevel commonsLogLevel : CommonsLogLevel.values()) {
			if (commonsLogLevel.name().equalsIgnoreCase(logLevel)) {
				return commonsLogLevel;
			}
		}
		throw new IllegalArgumentException("Unresolved log level " + logLevel
				+ " for apache commons logger, known levels " + Arrays.toString(CommonsLogLevel.values()));
	}

}
