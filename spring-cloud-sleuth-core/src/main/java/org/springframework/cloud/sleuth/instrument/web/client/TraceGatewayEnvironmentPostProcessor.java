/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.web.client;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

/**
 * Adds default properties for a gateway application.
 *
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
class TraceGatewayEnvironmentPostProcessor implements EnvironmentPostProcessor {

	private static final Log log = LogFactory.getLog(TraceGatewayEnvironmentPostProcessor.class);

	private static final String PROPERTY_SOURCE_NAME = "defaultProperties";

	@Override
	public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
		Map<String, Object> map = new HashMap<>();
		if (sleuthEnabled(environment) && isGatewayOnTheClasspath()) {
			String instrumentationType = environment.getProperty("spring.sleuth.reactor.instrumentation-type");
			if (log.isDebugEnabled()) {
				log.debug("Found the following instrumentation type [" + instrumentationType + "]");
			}
			if (StringUtils.isEmpty(instrumentationType)) {
				instrumentationType = "manual";
				if (log.isDebugEnabled()) {
					log.debug("No instrumentation type passed, will force it to [" + instrumentationType + "]");
				}
			}
			map.put("spring.sleuth.reactor.instrumentation-type", instrumentationType);
		}
		addOrReplace(environment.getPropertySources(), map);
	}

	private boolean sleuthEnabled(ConfigurableEnvironment environment) {
		return Boolean.parseBoolean(environment.getProperty("spring.sleuth.enabled", "true"));
	}

	private boolean isGatewayOnTheClasspath() {
		try {
			ClassUtils.forName("org.springframework.cloud.gateway.filter.GatewayFilter", null);
			return true;
		}
		catch (ClassNotFoundException e) {
			return false;
		}
	}

	private void addOrReplace(MutablePropertySources propertySources, Map<String, Object> map) {
		MapPropertySource target = null;
		if (propertySources.contains(PROPERTY_SOURCE_NAME)) {
			PropertySource<?> source = propertySources.get(PROPERTY_SOURCE_NAME);
			if (source instanceof MapPropertySource) {
				target = (MapPropertySource) source;
				for (String key : map.keySet()) {
					if (!target.containsProperty(key)) {
						target.getSource().put(key, map.get(key));
					}
				}
			}
		}
		if (target == null) {
			target = new MapPropertySource(PROPERTY_SOURCE_NAME, map);
		}
		if (!propertySources.contains(PROPERTY_SOURCE_NAME)) {
			propertySources.addLast(target);
		}
	}

}
