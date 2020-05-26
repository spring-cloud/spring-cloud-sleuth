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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import brave.baggage.BaggageField;
import brave.baggage.BaggagePropagation;
import brave.baggage.BaggagePropagationConfig.SingleBaggageField;
import brave.baggage.BaggagePropagationCustomizer;
import brave.propagation.B3Propagation;
import brave.propagation.B3Propagation.Format;
import brave.propagation.ExtraFieldCustomizer;
import brave.propagation.ExtraFieldPropagation;
import brave.propagation.Propagation;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.Nullable;

/**
 * {@link Configuration} for {@link BaggagePropagation}.
 * <p>
 *
 * @author Spencer Gibb
 * @author Marcin Grzejszczak
 * @since 2.0.0
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SleuthProperties.class)
class TraceBaggageConfiguration {

	static final Log logger = LogFactory.getLog(TraceBaggageConfiguration.class);

	static final String LOCAL_KEYS = "spring.sleuth.local-keys";
	static final String BAGGAGE_KEYS = "spring.sleuth.baggage-keys";
	static final String PROPAGATION_KEYS = "spring.sleuth.propagation-keys";

	// Note: Versions <2.2.3 use injectFormat(MULTI) for non-remote (ex spring-messaging)
	// See #1643
	static final Propagation.Factory B3_FACTORY =
			B3Propagation.newFactoryBuilder().injectFormat(Format.SINGLE_NO_PARENT).build();

	// These List<String> beans allow us to get deprecated property values, regardless of
	// if they were comma or yaml encoded. This keeps them out of SleuthBaggageProperties

	@Bean(BAGGAGE_KEYS)
	@ConfigurationProperties(BAGGAGE_KEYS)
	List<String> baggageKeys() {
		return new ArrayList<>();
	}

	@Bean(LOCAL_KEYS)
	@ConfigurationProperties(LOCAL_KEYS)
	List<String> localKeys() {
		return new ArrayList<>();
	}

	@Bean(PROPAGATION_KEYS)
	@ConfigurationProperties(PROPAGATION_KEYS)
	List<String> propagationKeys() {
		return new ArrayList<>();
	}

	/**
	 * To override the underlying context format, override this bean and set the delegate
	 * to what you need. {@link BaggagePropagation.FactoryBuilder} will unwrap itself if
	 * no fields are configured.
	 *
	 * <p>
	 * This will use {@link Format#SINGLE_NO_PARENT} for non-remote spans,
	 * such as for messaging. Note: it will still parse incoming multi-header spans.
	 */
	@Bean
	@ConditionalOnMissingBean
	BaggagePropagation.FactoryBuilder baggagePropagationFactoryBuilder() {
		return BaggagePropagation.newFactoryBuilder(B3_FACTORY);
	}

	Propagation.Factory sleuthPropagation(
			ExtraFieldPropagation.FactoryBuilder extraFieldPropagationFactoryBuilder,
			List<String> baggageKeys, List<String> localKeys,
			List<String> propagationKeys,
			@Nullable List<ExtraFieldCustomizer> extraFieldCustomizers) {
		if (extraFieldCustomizers == null) {
			extraFieldCustomizers = Collections.emptyList();
		}
		ExtraFieldPropagation.FactoryBuilder factoryBuilder;
		if (extraFieldPropagationFactoryBuilder != null) {
			factoryBuilder = extraFieldPropagationFactoryBuilder;
		}
		else {
			factoryBuilder = ExtraFieldPropagation.newFactoryBuilder(B3_FACTORY);
		}
		if (!baggageKeys.isEmpty()) {
			factoryBuilder
					// for HTTP
					.addPrefixedFields("baggage-", baggageKeys)
					// for messaging
					.addPrefixedFields("baggage_", baggageKeys);
		}
		for (String key : propagationKeys) {
			factoryBuilder.addField(key);
		}
		for (String key : localKeys) {
			factoryBuilder.addRedactedField(key);
		}
		for (ExtraFieldCustomizer customizer : extraFieldCustomizers) {
			customizer.customize(factoryBuilder);
		}
		return factoryBuilder.build();
	}

	@Bean
	@ConditionalOnMissingBean
	Propagation.Factory sleuthPropagation(
			@Nullable ExtraFieldPropagation.FactoryBuilder extraFieldPropagationFactoryBuilder,
			@Nullable List<ExtraFieldCustomizer> extraFieldCustomizers,
			BaggagePropagation.FactoryBuilder factoryBuilder,
			@Qualifier(BAGGAGE_KEYS) List<String> baggageKeys,
			@Qualifier(LOCAL_KEYS) List<String> localKeys,
			@Qualifier(PROPAGATION_KEYS) List<String> propagationKeys,
			@Nullable List<BaggagePropagationCustomizer> baggagePropagationCustomizers) {

		boolean useDeprecated = false;
		if (extraFieldPropagationFactoryBuilder != null) {
			logger.warn("ExtraFieldPropagation.FactoryBuilder is deprecated. "
					+ "Please switch to BaggagePropagation.FactoryBuilder");
			useDeprecated = true;
		}
		if (extraFieldCustomizers != null) {
			logger.warn("ExtraFieldCustomizer is deprecated. "
					+ "Please switch to BaggagePropagationCustomizer");
			useDeprecated = true;
		}
		if (useDeprecated) {
			return sleuthPropagation(extraFieldPropagationFactoryBuilder, localKeys,
					propagationKeys, baggageKeys, extraFieldCustomizers);
		}

		for (String fieldName : localKeys) {
			factoryBuilder.add(SingleBaggageField.local(BaggageField.create(fieldName)));
		}

		for (String fieldName : propagationKeys) {
			factoryBuilder.add(SingleBaggageField.remote(BaggageField.create(fieldName)));
		}

		for (String key : baggageKeys) {
			factoryBuilder.add(SingleBaggageField.newBuilder(BaggageField.create(key))
					.addKeyName("baggage-" + key) // for HTTP
					.addKeyName("baggage_" + key) // for messaging
					.build());
		}

		if (baggagePropagationCustomizers != null) {
			for (BaggagePropagationCustomizer customizer : baggagePropagationCustomizers) {
				customizer.customize(factoryBuilder);
			}
		}
		return factoryBuilder.build();
	}

}
