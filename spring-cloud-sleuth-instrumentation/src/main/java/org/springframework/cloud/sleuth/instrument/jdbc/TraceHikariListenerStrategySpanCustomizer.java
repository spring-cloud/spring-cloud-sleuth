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

import javax.sql.CommonDataSource;

import com.zaxxer.hikari.HikariDataSource;

import org.springframework.cloud.sleuth.Span;
import org.springframework.util.StringUtils;

/**
 * Customizer for {@link TraceListenerStrategy} for a {@link HikariDataSource}.
 *
 * @author Marcin Grzejszczak
 * @since 3.1.0
 */
public class TraceHikariListenerStrategySpanCustomizer
		implements TraceListenerStrategySpanCustomizer<HikariDataSource> {

	@Override
	public void customizeConnectionSpan(HikariDataSource hikariDataSource, Span.Builder spanBuilder) {
		if (StringUtils.hasText(hikariDataSource.getDriverClassName())) {
			spanBuilder.tag("sql.datasource.driver", hikariDataSource.getDriverClassName());
		}
		if (StringUtils.hasText(hikariDataSource.getPoolName())) {
			spanBuilder.tag("sql.datasource.pool", hikariDataSource.getPoolName());
		}
	}

	@Override
	public boolean isApplicable(CommonDataSource dataSource) {
		return dataSource instanceof HikariDataSource;
	}

}
