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

import org.springframework.cloud.sleuth.Span;

/**
 * Customizer for {@link TraceListenerStrategy} client span.
 *
 * @author Marcin Grzejszczak
 * @since 3.1.0
 */
public interface TraceListenerStrategySpanCustomizer<T extends CommonDataSource> {

	/**
	 * Customizes the client database span.
	 * @param spanBuilder span builder
	 */
	void customizeConnectionSpan(T dataSource, Span.Builder spanBuilder);

	/**
	 * @param dataSource data source for which we're building the span
	 * @return {@code true} when this customizer can be applied
	 */
	boolean isApplicable(CommonDataSource dataSource);

}
