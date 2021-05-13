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

package org.springframework.cloud.sleuth.autoconfig.instrument.r2dbc;

import java.util.function.Function;

import io.r2dbc.proxy.ProxyConnectionFactory;
import io.r2dbc.proxy.callback.ProxyConfig;
import io.r2dbc.spi.ConnectionFactory;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.sleuth.instrument.r2dbc.TraceProxyExecutionListener;

class TraceProxyConnectionFactoryWrapper implements Function<ConnectionFactory, ConnectionFactory> {

	private final BeanFactory beanFactory;

	private ObjectProvider<ProxyConfig> proxyConfig;

	TraceProxyConnectionFactoryWrapper(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}

	@Override
	public ConnectionFactory apply(ConnectionFactory connectionFactory) {
		ProxyConnectionFactory.Builder builder = ProxyConnectionFactory.builder(connectionFactory);
		proxyConfig().ifAvailable(builder::proxyConfig);
		builder.listener(new TraceProxyExecutionListener(this.beanFactory, connectionFactory));
		return builder.build();
	}

	private ObjectProvider<ProxyConfig> proxyConfig() {
		if (this.proxyConfig == null) {
			this.proxyConfig = this.beanFactory.getBeanProvider(ProxyConfig.class);
		}
		return this.proxyConfig;
	}

}
