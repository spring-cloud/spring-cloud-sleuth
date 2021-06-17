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
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.sleuth.instrument.jdbc.TraceType;

/**
 * Properties for JDBC instrumentation.
 *
 * @author Arthur Gavlyukovskiy
 * @since 3.1.0
 */
@ConfigurationProperties(prefix = "spring.sleuth.jdbc")
public class TraceJdbcProperties {

	/**
	 * Enables JDBC instrumentation.
	 */
	private boolean enabled = true;

	/**
	 * List of DataSource bean names that will not be decorated.
	 */
	private Collection<String> excludedDataSourceBeanNames = Collections.emptyList();

	/**
	 * Which types of tracing we would like to include.
	 */
	private List<TraceType> includes = Arrays.asList(TraceType.CONNECTION, TraceType.QUERY, TraceType.FETCH);

	private DataSourceProxyProperties datasourceProxy = new DataSourceProxyProperties();

	private P6SpyProperties p6spy = new P6SpyProperties();

	public boolean isEnabled() {
		return this.enabled;
	}

	public Collection<String> getExcludedDataSourceBeanNames() {
		return this.excludedDataSourceBeanNames;
	}

	public List<TraceType> getIncludes() {
		return this.includes;
	}

	public void setIncludes(List<TraceType> includes) {
		this.includes = includes;
	}

	public DataSourceProxyProperties getDatasourceProxy() {
		return datasourceProxy;
	}

	public P6SpyProperties getP6spy() {
		return this.p6spy;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public void setExcludedDataSourceBeanNames(Collection<String> excludedDataSourceBeanNames) {
		this.excludedDataSourceBeanNames = excludedDataSourceBeanNames;
	}

	public void setDatasourceProxy(DataSourceProxyProperties datasourceProxy) {
		this.datasourceProxy = datasourceProxy;
	}

	public void setP6spy(P6SpyProperties p6spy) {
		this.p6spy = p6spy;
	}

	/**
	 * Properties for datasource-proxy.
	 */
	public static class DataSourceProxyProperties {

		/**
		 * Should the datasource-proxy tracing be enabled?
		 */
		private boolean enabled = true;

		/**
		 * Logging to use for logging queries.
		 */
		private DataSourceProxyLogging logging = DataSourceProxyLogging.SLF4J;

		/**
		 * Query configuration.
		 */
		private Query query = new Query();

		/**
		 * Slow query configuration.
		 */
		private SlowQuery slowQuery = new SlowQuery();

		/**
		 * Use multiline output for logging query.
		 *
		 * @see ProxyDataSourceBuilder#multiline()
		 */
		private boolean multiline = true;

		/**
		 * Use json output for logging query.
		 *
		 * @see ProxyDataSourceBuilder#asJson()
		 */
		private boolean jsonFormat = false;

		public boolean isEnabled() {
			return this.enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		public DataSourceProxyLogging getLogging() {
			return this.logging;
		}

		public void setLogging(DataSourceProxyLogging logging) {
			this.logging = logging;
		}

		public Query getQuery() {
			return this.query;
		}

		public void setQuery(Query query) {
			this.query = query;
		}

		public SlowQuery getSlowQuery() {
			return this.slowQuery;
		}

		public void setSlowQuery(SlowQuery slowQuery) {
			this.slowQuery = slowQuery;
		}

		public boolean isMultiline() {
			return this.multiline;
		}

		public void setMultiline(boolean multiline) {
			this.multiline = multiline;
		}

		public boolean isJsonFormat() {
			return this.jsonFormat;
		}

		public void setJsonFormat(boolean jsonFormat) {
			this.jsonFormat = jsonFormat;
		}

		/**
		 * Properties to configure query logging listener.
		 */
		public static class Query {

			/**
			 * Enable logging all queries to the log.
			 */
			private boolean enableLogging = false;

			/**
			 * Name of query logger.
			 */
			private String loggerName;

			/**
			 * Severity of query logger.
			 */
			private String logLevel = "DEBUG";

			public boolean isEnableLogging() {
				return this.enableLogging;
			}

			public void setEnableLogging(boolean enableLogging) {
				this.enableLogging = enableLogging;
			}

			public String getLoggerName() {
				return this.loggerName;
			}

			public void setLoggerName(String loggerName) {
				this.loggerName = loggerName;
			}

			public String getLogLevel() {
				return this.logLevel;
			}

			public void setLogLevel(String logLevel) {
				this.logLevel = logLevel;
			}

		}

		/**
		 * Properties to configure slow query logging listener.
		 */
		public static class SlowQuery {

			/**
			 * Enable logging slow queries to the log.
			 */
			private boolean enableLogging = false;

			/**
			 * Name of slow query logger.
			 */
			private String loggerName;

			/**
			 * Severity of slow query logger.
			 */
			private String logLevel = "WARN";

			/**
			 * Number of seconds to consider query as slow.
			 */
			private long threshold = 300;

			public boolean isEnableLogging() {
				return enableLogging;
			}

			public void setEnableLogging(boolean enableLogging) {
				this.enableLogging = enableLogging;
			}

			public String getLoggerName() {
				return loggerName;
			}

			public void setLoggerName(String loggerName) {
				this.loggerName = loggerName;
			}

			public String getLogLevel() {
				return logLevel;
			}

			public void setLogLevel(String logLevel) {
				this.logLevel = logLevel;
			}

			public long getThreshold() {
				return threshold;
			}

			public void setThreshold(long threshold) {
				this.threshold = threshold;
			}

		}

		/**
		 * Query logging listener is the most used listener that logs executing query with
		 * actual parameters to. You can pick one of the following proxy logging
		 * mechanisms.
		 */
		public enum DataSourceProxyLogging {

			/**
			 * Log using System.out.
			 */
			SYSOUT,

			/**
			 * Log using SLF4J.
			 */
			SLF4J,

			/**
			 * Log using Commons.
			 */
			COMMONS,

			/**
			 * Log using Java Util Logging.
			 */
			JUL

		}

	}

	/**
	 * Properties for configuring p6spy.
	 */
	public static class P6SpyProperties {

		/**
		 * Should the p6spy tracing be enabled?
		 */
		private boolean enabled = true;

		/**
		 * Enables logging JDBC events.
		 */
		private boolean enableLogging = false;

		/**
		 * Enables multiline output.
		 */
		private boolean multiline = true;

		/**
		 * Logging to use for logging queries.
		 */
		private P6SpyLogging logging = P6SpyLogging.SLF4J;

		/**
		 * Name of log file to use (only with logging=file).
		 */
		private String logFile = "spy.log";

		/**
		 * Custom log format.
		 */
		private String logFormat;

		/**
		 * Tracing related properties.
		 */
		private P6SpyTracing tracing = new P6SpyTracing();

		/**
		 * Class file to use (only with logging=custom). The class must implement
		 * {@link com.p6spy.engine.spy.appender.FormattedLogger}.
		 */
		private String customAppenderClass;

		/**
		 * Log filtering related properties.
		 */
		private P6SpyLogFilter logFilter = new P6SpyLogFilter();

		public boolean isEnabled() {
			return this.enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		public boolean isEnableLogging() {
			return this.enableLogging;
		}

		public void setEnableLogging(boolean enableLogging) {
			this.enableLogging = enableLogging;
		}

		public boolean isMultiline() {
			return this.multiline;
		}

		public void setMultiline(boolean multiline) {
			this.multiline = multiline;
		}

		public P6SpyLogging getLogging() {
			return this.logging;
		}

		public void setLogging(P6SpyLogging logging) {
			this.logging = logging;
		}

		public String getLogFile() {
			return this.logFile;
		}

		public void setLogFile(String logFile) {
			this.logFile = logFile;
		}

		public String getLogFormat() {
			return this.logFormat;
		}

		public void setLogFormat(String logFormat) {
			this.logFormat = logFormat;
		}

		public P6SpyTracing getTracing() {
			return this.tracing;
		}

		public void setTracing(P6SpyTracing tracing) {
			this.tracing = tracing;
		}

		public String getCustomAppenderClass() {
			return this.customAppenderClass;
		}

		public void setCustomAppenderClass(String customAppenderClass) {
			this.customAppenderClass = customAppenderClass;
		}

		public P6SpyLogFilter getLogFilter() {
			return this.logFilter;
		}

		public void setLogFilter(P6SpyLogFilter logFilter) {
			this.logFilter = logFilter;
		}

		/**
		 * P6Spy logging options.
		 */
		public enum P6SpyLogging {

			/**
			 * Log using System.out.
			 */
			SYSOUT,

			/**
			 * Log using SLF4J.
			 */
			SLF4J,

			/**
			 * Log to file.
			 */
			FILE,

			/**
			 * Custom logging.
			 */
			CUSTOM

		}

		public static class P6SpyTracing {

			/**
			 * Report the effective sql string (with '?' replaced with real values) to
			 * tracing systems.
			 * <p>
			 * NOTE this setting does not affect the logging message.
			 */
			private boolean includeParameterValues = true;

			public boolean isIncludeParameterValues() {
				return this.includeParameterValues;
			}

			public void setIncludeParameterValues(boolean includeParameterValues) {
				this.includeParameterValues = includeParameterValues;
			}

		}

		public static class P6SpyLogFilter {

			/**
			 * Use regex pattern to filter log messages. Only matched messages will be
			 * logged.
			 */
			private Pattern pattern;

			public Pattern getPattern() {
				return this.pattern;
			}

			public void setPattern(Pattern pattern) {
				this.pattern = pattern;
			}

		}

	}

}
