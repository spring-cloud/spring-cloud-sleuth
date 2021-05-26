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

import net.ttddyy.dsproxy.QueryCountHolder;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.sleuth.instrument.jdbc.TraceType;

/**
 * Properties for configuring proxy providers.
 *
 * @author Arthur Gavlyukovskiy
 * @since 3.1.0
 */
@ConfigurationProperties(prefix = "spring.sleuth.jdbc.decorator.datasource")
public class TraceDataSourceDecoratorProperties {

	/**
	 * Enables data source decorating.
	 */
	private boolean enabled = true;

	/**
	 * Beans that won't be decorated.
	 */
	private Collection<String> excludedBeans = Collections.emptyList();

	/**
	 * Which types of tracing we would like to include.
	 */
	private List<TraceType> includes = Arrays.asList(TraceType.CONNECTION, TraceType.QUERY, TraceType.FETCH);

	private DataSourceProxyProperties datasourceProxy = new DataSourceProxyProperties();

	private P6SpyProperties p6spy = new P6SpyProperties();

	public boolean isEnabled() {
		return enabled;
	}

	public Collection<String> getExcludedBeans() {
		return excludedBeans;
	}

	public List<TraceType> getIncludes() {
		return includes;
	}

	public void setIncludes(List<TraceType> includes) {
		this.includes = includes;
	}

	public DataSourceProxyProperties getDatasourceProxy() {
		return datasourceProxy;
	}

	public P6SpyProperties getP6spy() {
		return p6spy;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public void setExcludedBeans(Collection<String> excludedBeans) {
		this.excludedBeans = excludedBeans;
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

		/**
		 * Creates listener to count queries.
		 *
		 * @see ProxyDataSourceBuilder#countQuery()
		 * @see QueryCountHolder
		 */
		private boolean countQuery = false;

		public DataSourceProxyLogging getLogging() {
			return logging;
		}

		public void setLogging(DataSourceProxyLogging logging) {
			this.logging = logging;
		}

		public Query getQuery() {
			return query;
		}

		public void setQuery(Query query) {
			this.query = query;
		}

		public SlowQuery getSlowQuery() {
			return slowQuery;
		}

		public void setSlowQuery(SlowQuery slowQuery) {
			this.slowQuery = slowQuery;
		}

		public boolean isMultiline() {
			return multiline;
		}

		public void setMultiline(boolean multiline) {
			this.multiline = multiline;
		}

		public boolean isJsonFormat() {
			return jsonFormat;
		}

		public void setJsonFormat(boolean jsonFormat) {
			this.jsonFormat = jsonFormat;
		}

		public boolean isCountQuery() {
			return countQuery;
		}

		public void setCountQuery(boolean countQuery) {
			this.countQuery = countQuery;
		}

		/**
		 * Properties to configure query logging listener.
		 */
		public static class Query {

			/**
			 * Enable logging all queries to the log.
			 */
			private boolean enableLogging = true;

			/**
			 * Name of query logger.
			 */
			private String loggerName;

			/**
			 * Severity of query logger.
			 */
			private String logLevel = "DEBUG";

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

		}

		/**
		 * Properties to configure slow query logging listener.
		 */
		public static class SlowQuery {

			/**
			 * Enable logging slow queries to the log.
			 */
			private boolean enableLogging = true;

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

			boolean isEnableLogging() {
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
		 * Enables logging JDBC events.
		 */
		private boolean enableLogging = true;

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

		public boolean isEnableLogging() {
			return enableLogging;
		}

		public void setEnableLogging(boolean enableLogging) {
			this.enableLogging = enableLogging;
		}

		public boolean isMultiline() {
			return multiline;
		}

		public void setMultiline(boolean multiline) {
			this.multiline = multiline;
		}

		public P6SpyLogging getLogging() {
			return logging;
		}

		public void setLogging(P6SpyLogging logging) {
			this.logging = logging;
		}

		public String getLogFile() {
			return logFile;
		}

		public void setLogFile(String logFile) {
			this.logFile = logFile;
		}

		public String getLogFormat() {
			return logFormat;
		}

		public void setLogFormat(String logFormat) {
			this.logFormat = logFormat;
		}

		public P6SpyTracing getTracing() {
			return tracing;
		}

		public void setTracing(P6SpyTracing tracing) {
			this.tracing = tracing;
		}

		public String getCustomAppenderClass() {
			return customAppenderClass;
		}

		public void setCustomAppenderClass(String customAppenderClass) {
			this.customAppenderClass = customAppenderClass;
		}

		public P6SpyLogFilter getLogFilter() {
			return logFilter;
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
				return includeParameterValues;
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
				return pattern;
			}

			public void setPattern(Pattern pattern) {
				this.pattern = pattern;
			}

		}

	}

}
