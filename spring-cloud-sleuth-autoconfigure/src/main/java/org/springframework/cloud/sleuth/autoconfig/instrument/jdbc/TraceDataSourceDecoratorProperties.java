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

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.cloud.sleuth.instrument.jdbc.DataSourceProxyProperties;
import org.springframework.cloud.sleuth.instrument.jdbc.P6SpyProperties;
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

	@NestedConfigurationProperty
	private DataSourceProxyProperties datasourceProxy = new DataSourceProxyProperties();

	@NestedConfigurationProperty
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

}
