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
import java.util.Collections;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.cloud.sleuth.instrument.jdbc.DataSourceProxyProperties;
import org.springframework.cloud.sleuth.instrument.jdbc.P6SpyProperties;

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
	private Collection<String> excludeBeans = Collections.emptyList();

	@NestedConfigurationProperty
	private DataSourceProxyProperties datasourceProxy = new DataSourceProxyProperties();

	@NestedConfigurationProperty
	private P6SpyProperties p6spy = new P6SpyProperties();

	@NestedConfigurationProperty
	private SleuthProperties sleuth = new SleuthProperties();

	public boolean isEnabled() {
		return enabled;
	}

	public Collection<String> getExcludeBeans() {
		return excludeBeans;
	}

	public DataSourceProxyProperties getDatasourceProxy() {
		return datasourceProxy;
	}

	public P6SpyProperties getP6spy() {
		return p6spy;
	}

	public SleuthProperties getSleuth() {
		return sleuth;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public void setExcludeBeans(Collection<String> excludeBeans) {
		this.excludeBeans = excludeBeans;
	}

	public void setDatasourceProxy(DataSourceProxyProperties datasourceProxy) {
		this.datasourceProxy = datasourceProxy;
	}

	public void setP6spy(P6SpyProperties p6spy) {
		this.p6spy = p6spy;
	}

	public void setSleuth(SleuthProperties sleuth) {
		this.sleuth = sleuth;
	}

}
