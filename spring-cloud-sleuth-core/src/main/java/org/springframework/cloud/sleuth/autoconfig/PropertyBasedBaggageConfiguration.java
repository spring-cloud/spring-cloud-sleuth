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

package org.springframework.cloud.sleuth.autoconfig;

import java.util.LinkedHashSet;
import java.util.Set;

import brave.baggage.BaggageField;
import brave.baggage.BaggageFields;
import brave.baggage.BaggagePropagationConfig;
import brave.baggage.BaggagePropagationConfig.SingleBaggageField;
import brave.baggage.CorrelationScopeConfig;
import brave.baggage.CorrelationScopeConfig.SingleCorrelationField;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Wire up property-based {@linkplain BaggagePropagationConfig} and
 * {@link CorrelationScopeConfig} so that they appear as if they were defined one-by-one.
 * This allows users to contribute configs and also for us to use the list of them above.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(value = "spring.sleuth.enabled", matchIfMissing = true)
public class PropertyBasedBaggageConfiguration implements BeanFactoryPostProcessor {

	static final Log logger = LogFactory.getLog(PropertyBasedBaggageConfiguration.class);

	@Override
	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
		Environment env = beanFactory.getBean(Environment.class);

		Set<SingleBaggageField> baggageConfigs = parseBaggageConfigsFromProperty(env);

		for (SingleBaggageField config : baggageConfigs) {
			beanFactory.registerSingleton(config.field().name() + "BaggageField", config);
		}

		Set<SingleCorrelationField> correlationConfigs = parseCorrelationConfigsFromProperty(
				env);

		for (SingleCorrelationField config : correlationConfigs) {
			beanFactory.registerSingleton(config.name() + "CorrelationField", config);
		}
	}

	static Set<SingleBaggageField> parseBaggageConfigsFromProperty(Environment env) {
		Set<SingleBaggageField> baggageConfigs = new LinkedHashSet<>();

		for (String key : collectKeysOfType(env, "local")) {
			baggageConfigs.add(SingleBaggageField.local(BaggageField.create(key)));
		}

		for (String key : collectKeysOfType(env, "remote")) {
			baggageConfigs.add(SingleBaggageField.remote(BaggageField.create(key)));
		}

		if (!collectKeysOfType(env, "propagation").isEmpty()) {
			logger.warn(
					"Property 'spring.sleuth.propagation-keys' has been renamed to 'spring.sleuth.remote-keys'.");
		}

		if (!collectKeysOfType(env, "baggage").isEmpty()) {
			logger.warn("Property 'spring.sleuth.baggage-keys' is no longer read.\n"
					+ "To change header names define a @Bean of type "
					+ SingleBaggageField.class.getName());
		}
		return baggageConfigs;
	}

	static Set<SingleCorrelationField> parseCorrelationConfigsFromProperty(
			Environment env) {
		Set<SingleCorrelationField> correlationConfigs = new LinkedHashSet<>();
		correlationConfigs.add(SingleCorrelationField.create(BaggageFields.TRACE_ID));
		correlationConfigs.add(SingleCorrelationField.create(BaggageFields.PARENT_ID));
		correlationConfigs.add(SingleCorrelationField.create(BaggageFields.SPAN_ID));
		correlationConfigs.add(SingleCorrelationField.newBuilder(BaggageFields.SAMPLED)
				.name("spanExportable").build());

		for (String key : collectKeysOfType(env, "log.slf4j.whitelisted-mdc")) {
			// For backwards compatibility set all fields dirty, so that any changes made
			// by MDC directly are reverted.
			correlationConfigs.add(SingleCorrelationField
					.newBuilder(BaggageField.create(key)).dirty().build());
		}
		return correlationConfigs;
	}

	static Set<String> collectKeysOfType(Environment env, String type) {
		String propertyName = "spring.sleuth." + type + "-keys";
		Set<String> result = new LinkedHashSet<>();
		for (String key : env.getProperty(propertyName, "").split(",")) {
			if (key == null) {
				continue;
			}
			key = key.trim();
			if (key.isEmpty()) {
				continue;
			}
			result.add(key);
		}
		return result;
	}

}
