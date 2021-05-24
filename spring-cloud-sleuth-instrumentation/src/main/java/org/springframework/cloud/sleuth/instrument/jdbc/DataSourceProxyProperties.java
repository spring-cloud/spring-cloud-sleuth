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

import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import net.ttddyy.dsproxy.QueryCountHolder;
import net.ttddyy.dsproxy.listener.logging.CommonsLogLevel;
import net.ttddyy.dsproxy.listener.logging.SLF4JLogLevel;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;

/**
 * Properties for datasource-proxy.
 *
 * @author Arthur Gavlyukovskiy
 * @since 3.1.0
 */
public class DataSourceProxyProperties {

	/**
	 * Logging to use for logging queries.
	 */
	private DataSourceProxyLogging logging = DataSourceProxyLogging.SLF4J;

	private Query query = new Query();

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
	 *
	 * @see ProxyDataSourceBuilder#logQueryToSysOut()
	 * @see ProxyDataSourceBuilder#logQueryBySlf4j(SLF4JLogLevel, String)
	 * @see ProxyDataSourceBuilder#logQueryByCommons(CommonsLogLevel, String)
	 * @see ProxyDataSourceBuilder#logQueryByJUL(Level, String)
	 */
	static class Query {

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
	 *
	 * @see ProxyDataSourceBuilder#logSlowQueryToSysOut(long, TimeUnit)
	 * @see ProxyDataSourceBuilder#logSlowQueryBySlf4j(long, TimeUnit)
	 * @see ProxyDataSourceBuilder#logSlowQueryByCommons(long, TimeUnit)
	 * @see ProxyDataSourceBuilder#logSlowQueryByJUL(long, TimeUnit)
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
	 * actual parameters to. You can pick one of the following proxy logging mechanisms.
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
