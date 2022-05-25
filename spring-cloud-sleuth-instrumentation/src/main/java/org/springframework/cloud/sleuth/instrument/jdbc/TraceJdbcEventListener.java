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

import java.sql.SQLException;
import java.util.List;

import javax.sql.CommonDataSource;

import com.p6spy.engine.common.ConnectionInformation;
import com.p6spy.engine.common.PreparedStatementInformation;
import com.p6spy.engine.common.ResultSetInformation;
import com.p6spy.engine.common.StatementInformation;
import com.p6spy.engine.event.SimpleJdbcEventListener;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.core.Ordered;
import org.springframework.util.StringUtils;

/**
 * Listener to represent each connection and sql query as a span.
 *
 * @author Arthur Gavlyukovskiy
 * @since 3.1.0
 */
public class TraceJdbcEventListener extends SimpleJdbcEventListener implements Ordered {

	/**
	 * Bean order.
	 */
	public static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 10;

	private final DataSourceNameResolver dataSourceNameResolver;

	private final TraceListenerStrategy<ConnectionInformation, StatementInformation, ResultSetInformation> strategy;

	private final boolean includeParameterValues;

	public TraceJdbcEventListener(BeanFactory beanFactory, DataSourceNameResolver dataSourceNameResolver,
			List<TraceType> traceTypes, boolean includeParameterValues,
			List<TraceListenerStrategySpanCustomizer<? super CommonDataSource>> customizers) {
		this.dataSourceNameResolver = dataSourceNameResolver;
		this.includeParameterValues = includeParameterValues;
		this.strategy = new TraceListenerStrategy<>(beanFactory, traceTypes, customizers);
	}

	@Override
	public void onBeforeGetConnection(ConnectionInformation connectionInformation) {
		CommonDataSource dataSource = connectionInformation.getDataSource();
		String dataSourceName = this.dataSourceNameResolver.resolveDataSourceName(dataSource);
		this.strategy.beforeGetConnection(connectionInformation, dataSource, dataSourceName);
	}

	@Override
	public void onAfterGetConnection(ConnectionInformation connectionInformation, SQLException e) {
		CommonDataSource dataSource = connectionInformation.getDataSource();
		String dataSourceName = this.dataSourceNameResolver.resolveDataSourceName(dataSource);
		this.strategy.afterGetConnection(connectionInformation, connectionInformation.getConnection(), dataSourceName,
				e);
	}

	@Override
	public void onBeforeAnyExecute(StatementInformation statementInformation) {
		this.strategy.beforeQuery(statementInformation.getConnectionInformation(), statementInformation);
	}

	@Override
	public void onAfterAnyExecute(StatementInformation statementInformation, long timeElapsedNanos, SQLException e) {
		this.strategy.afterQuery(statementInformation.getConnectionInformation(), statementInformation,
				getSql(statementInformation), e);
	}

	@Override
	public void onBeforeResultSetNext(ResultSetInformation resultSetInformation) {
		this.strategy.beforeResultSetNext(resultSetInformation.getConnectionInformation(),
				resultSetInformation.getStatementInformation(), resultSetInformation);
	}

	@Override
	public void onAfterExecuteUpdate(PreparedStatementInformation statementInformation, long timeElapsedNanos,
			int rowCount, SQLException e) {
		if (e == null) {
			this.strategy.addQueryRowCount(statementInformation.getConnectionInformation(), statementInformation,
					rowCount);
		}
		super.onAfterExecuteUpdate(statementInformation, timeElapsedNanos, rowCount, e);
	}

	@Override
	public void onAfterExecuteUpdate(StatementInformation statementInformation, long timeElapsedNanos, String sql,
			int rowCount, SQLException e) {
		if (e == null) {
			this.strategy.addQueryRowCount(statementInformation.getConnectionInformation(), statementInformation,
					rowCount);
		}
		super.onAfterExecuteUpdate(statementInformation, timeElapsedNanos, sql, rowCount, e);
	}

	@Override
	public void onAfterStatementClose(StatementInformation statementInformation, SQLException e) {
		this.strategy.afterStatementClose(statementInformation.getConnectionInformation(), statementInformation);
	}

	@Override
	public void onAfterResultSetClose(ResultSetInformation resultSetInformation, SQLException e) {
		this.strategy.afterResultSetClose(resultSetInformation.getConnectionInformation(), resultSetInformation,
				resultSetInformation.getCurrRow() + 1, e);
	}

	@Override
	public void onAfterCommit(ConnectionInformation connectionInformation, long timeElapsedNanos, SQLException e) {
		this.strategy.afterCommit(connectionInformation, e);
	}

	@Override
	public void onAfterRollback(ConnectionInformation connectionInformation, long timeElapsedNanos, SQLException e) {
		this.strategy.afterRollback(connectionInformation, e);
	}

	@Override
	public void onAfterConnectionClose(ConnectionInformation connectionInformation, SQLException e) {
		this.strategy.afterConnectionClose(connectionInformation, e);
	}

	private String getSql(StatementInformation statementInformation) {
		return this.includeParameterValues && StringUtils.hasText(statementInformation.getSqlWithValues())
				? statementInformation.getSqlWithValues() : statementInformation.getSql();
	}

	@Override
	public int getOrder() {
		return ORDER;
	}

}
