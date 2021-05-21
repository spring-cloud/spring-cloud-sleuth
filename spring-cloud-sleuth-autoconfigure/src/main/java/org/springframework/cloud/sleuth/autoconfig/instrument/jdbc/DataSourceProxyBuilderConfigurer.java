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

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
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
import org.slf4j.Logger;

import org.springframework.beans.factory.annotation.Autowired;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Configurer for {@link ProxyDataSourceBuilder} based on the application context.
 *
 * @see ProxyDataSourceBuilder
 * @author Arthur Gavlyukovskiy
 * @since 1.3.1
 */
class DataSourceProxyBuilderConfigurer {

	private static final Logger log = getLogger(DataSourceProxyBuilderConfigurer.class);

	@Autowired(required = false)
	private QueryCountStrategy queryCountStrategy;

	@Autowired(required = false)
	private List<QueryExecutionListener> listeners;

	@Autowired(required = false)
	private List<MethodExecutionListener> methodExecutionListeners;

	@Autowired(required = false)
	private ParameterTransformer parameterTransformer;

	@Autowired(required = false)
	private QueryTransformer queryTransformer;

	@Autowired(required = false)
	private ResultSetProxyLogicFactory resultSetProxyLogicFactory;

	@Autowired(required = false)
	private DataSourceProxyConnectionIdManagerProvider dataSourceProxyConnectionIdManagerProvider;

	public void configure(ProxyDataSourceBuilder proxyDataSourceBuilder, DataSourceProxyProperties datasourceProxy) {
		switch (datasourceProxy.getLogging()) {
		case SLF4J: {
			if (datasourceProxy.getQuery().isEnableLogging()) {
				proxyDataSourceBuilder.logQueryBySlf4j(toSlf4JLogLevel(datasourceProxy.getQuery().getLogLevel()),
						datasourceProxy.getQuery().getLoggerName());
			}
			if (datasourceProxy.getSlowQuery().isEnableLogging()) {
				proxyDataSourceBuilder.logSlowQueryBySlf4j(datasourceProxy.getSlowQuery().getThreshold(),
						TimeUnit.SECONDS, toSlf4JLogLevel(datasourceProxy.getSlowQuery().getLogLevel()),
						datasourceProxy.getSlowQuery().getLoggerName());
			}
			break;
		}
		case JUL: {
			if (datasourceProxy.getQuery().isEnableLogging()) {
				proxyDataSourceBuilder.logQueryByJUL(toJULLogLevel(datasourceProxy.getQuery().getLogLevel()),
						datasourceProxy.getQuery().getLoggerName());
			}
			if (datasourceProxy.getSlowQuery().isEnableLogging()) {
				proxyDataSourceBuilder.logSlowQueryByJUL(datasourceProxy.getSlowQuery().getThreshold(),
						TimeUnit.SECONDS, toJULLogLevel(datasourceProxy.getSlowQuery().getLogLevel()),
						datasourceProxy.getSlowQuery().getLoggerName());
			}
			break;
		}
		case COMMONS: {
			if (datasourceProxy.getQuery().isEnableLogging()) {
				proxyDataSourceBuilder.logQueryByCommons(toCommonsLogLevel(datasourceProxy.getQuery().getLogLevel()),
						datasourceProxy.getQuery().getLoggerName());
			}
			if (datasourceProxy.getSlowQuery().isEnableLogging()) {
				proxyDataSourceBuilder.logSlowQueryByCommons(datasourceProxy.getSlowQuery().getThreshold(),
						TimeUnit.SECONDS, toCommonsLogLevel(datasourceProxy.getSlowQuery().getLogLevel()),
						datasourceProxy.getSlowQuery().getLoggerName());
			}
			break;
		}
		case SYSOUT: {
			if (datasourceProxy.getQuery().isEnableLogging()) {
				proxyDataSourceBuilder.logQueryToSysOut();
			}
			if (datasourceProxy.getSlowQuery().isEnableLogging()) {
				proxyDataSourceBuilder.logSlowQueryToSysOut(datasourceProxy.getSlowQuery().getThreshold(),
						TimeUnit.SECONDS);
			}
			break;
		}
		}
		if (datasourceProxy.isMultiline() && datasourceProxy.isJsonFormat()) {
			log.warn(
					"Found opposite multiline and json format, multiline will be used (may depend on library version)");
		}
		if (datasourceProxy.isMultiline()) {
			proxyDataSourceBuilder.multiline();
		}
		if (datasourceProxy.isJsonFormat()) {
			proxyDataSourceBuilder.asJson();
		}
		if (datasourceProxy.isCountQuery()) {
			proxyDataSourceBuilder.countQuery(queryCountStrategy);
		}
		if (listeners != null) {
			listeners.forEach(proxyDataSourceBuilder::listener);
		}
		if (methodExecutionListeners != null) {
			methodExecutionListeners.forEach(proxyDataSourceBuilder::methodListener);
		}
		if (parameterTransformer != null) {
			proxyDataSourceBuilder.parameterTransformer(parameterTransformer);
		}
		if (queryTransformer != null) {
			proxyDataSourceBuilder.queryTransformer(queryTransformer);
		}
		if (resultSetProxyLogicFactory != null) {
			proxyDataSourceBuilder.proxyResultSet(resultSetProxyLogicFactory);
		}
		if (dataSourceProxyConnectionIdManagerProvider != null) {
			proxyDataSourceBuilder.connectionIdManager(dataSourceProxyConnectionIdManagerProvider.get());
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
		throw new IllegalArgumentException("Unresolved log level " + logLevel + " for slf4j logger, " + "known levels: "
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
		throw new IllegalArgumentException("Unresolved log level " + logLevel + " for apache commons logger, "
				+ "known levels " + Arrays.toString(CommonsLogLevel.values()));
	}

}
