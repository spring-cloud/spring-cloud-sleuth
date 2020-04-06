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

package org.springframework.cloud.sleuth.baggage;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import brave.baggage.BaggageField;
import brave.baggage.BaggagePropagation;
import brave.baggage.BaggagePropagationConfig.SingleBaggageField;
import brave.baggage.BaggagePropagationCustomizer;
import brave.baggage.CorrelationScopeConfig.SingleCorrelationField;
import brave.baggage.CorrelationScopeDecorator;
import brave.context.slf4j.MDCScopeDecorator;
import brave.handler.FinishedSpanHandler;
import brave.propagation.B3Propagation;
import brave.propagation.CurrentTraceContext.ScopeDecorator;
import brave.propagation.Propagation;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.slf4j.MDC;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.sleuth.autoconfig.TraceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration
 * Auto-configuration} for {@link BaggagePropagation}.
 * <p>
 *
 * @author Spencer Gibb
 * @author Marcin Grzejszczak
 * @since 2.0.0
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(value = "spring.sleuth.enabled", matchIfMissing = true)
@AutoConfigureBefore(TraceAutoConfiguration.class)
@EnableConfigurationProperties(SleuthBaggageProperties.class)
public class TraceBaggageAutoConfiguration {

	static final Log logger = LogFactory.getLog(TraceBaggageAutoConfiguration.class);

	@Autowired(required = false)
	List<BaggagePropagationCustomizer> baggagePropagationCustomizers = new ArrayList<>();

	/**
	 * To override the underlying context format, override this bean and set the delegate
	 * to what you need. {@link BaggagePropagation.FactoryBuilder} will unwrap itself if
	 * no fields are configured.
	 */
	@Bean
	@ConditionalOnMissingBean
	BaggagePropagation.FactoryBuilder baggagePropagationFactoryBuilder() {
		return BaggagePropagation.newFactoryBuilder(B3Propagation.FACTORY);
	}

	@Bean
	@ConditionalOnMissingBean
	Propagation.Factory sleuthPropagation(
			BaggagePropagation.FactoryBuilder factoryBuilder,
			@Value("${spring.sleuth.baggage-keys:}") String baggageKeys,
			@Value("${spring.sleuth.local-keys:}") String localKeys,
			@Value("${spring.sleuth.propagation-keys:}") String propagationKeys,
			SleuthBaggageProperties sleuthBaggageProperties) {

		Set<String> localFields = redirectOldPropertyToNew("spring.sleuth.local-keys",
				collectFieldsFromProperty(localKeys),
				"spring.sleuth.baggage.local-fields",
				sleuthBaggageProperties.getLocalFields());
		for (String fieldName : localFields) {
			factoryBuilder.add(SingleBaggageField.local(BaggageField.create(fieldName)));
		}

		Set<String> remoteFields = redirectOldPropertyToNew(
				"spring.sleuth.propagation-keys",
				collectFieldsFromProperty(propagationKeys),
				"spring.sleuth.baggage.remote-fields",
				sleuthBaggageProperties.getRemoteFields());
		for (String fieldName : remoteFields) {
			factoryBuilder.add(SingleBaggageField.remote(BaggageField.create(fieldName)));
		}

		if (!baggageKeys.isEmpty()) {
			logger.warn(
					"'spring.sleuth.baggage-keys' will be removed in a future release.\n"
							+ "To change header names define a @Bean of type "
							+ SingleBaggageField.class.getName());

			for (String key : collectFieldsFromProperty(baggageKeys)) {
				factoryBuilder.add(SingleBaggageField.newBuilder(BaggageField.create(key))
						.addKeyName("baggage-" + key) // for HTTP
						.addKeyName("baggage_" + key) // for messaging
						.build());
			}
		}

		for (BaggagePropagationCustomizer customizer : this.baggagePropagationCustomizers) {
			customizer.customize(factoryBuilder);
		}
		return factoryBuilder.build();
	}

	@Bean
	FinishedSpanHandler baggageTagFinishedSpanHandler(
			@Value("${spring.sleuth.propagation.tag.whitelisted-keys:}") String whitelistedKeys,
			SleuthBaggageProperties sleuthBaggageProperties) {

		Set<String> tagFields = redirectOldPropertyToNew(
				"spring.sleuth.propagation.tag.whitelisted-keys",
				collectFieldsFromProperty(whitelistedKeys),
				"spring.sleuth.baggage.tag-fields",
				sleuthBaggageProperties.getTagFields());

		if (tagFields.isEmpty()) {
			return FinishedSpanHandler.NOOP; // Brave ignores these
		}

		return new BaggageTagFinishedSpanHandler(tagFields.stream()
				.map(BaggageField::create).toArray(BaggageField[]::new));
	}

	static Set<String> redirectOldPropertyToNew(String oldProperty, Set<String> oldValue,
			String newProperty, List<String> newValue) {
		Set<String> result = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		result.addAll(newValue);
		if (!oldValue.isEmpty()) {
			logger.warn("'" + oldProperty + "' has been renamed to '" + newProperty
					+ "' and will be removed in a future release.");
			result.addAll(oldValue); // dedupes
		}
		return result;
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnClass(MDC.class)
	CorrelationScopeDecorator.Builder correlationScopeDecoratorBuilder() {
		return MDCScopeDecorator.newBuilder();
	}

	@Bean
	@ConditionalOnMissingBean(CorrelationScopeDecorator.class)
	@ConditionalOnBean(CorrelationScopeDecorator.Builder.class)
	@ConditionalOnProperty(value = "spring.sleuth.baggage.correlation-enabled",
			matchIfMissing = true)
	ScopeDecorator correlationScopeDecorator(
			@Value("${spring.sleuth.log.slf4j.whitelisted-mdc-keys:}") String whitelistedKeys,
			SleuthBaggageProperties sleuthBaggageProperties) {

		Set<String> correlationFields = redirectOldPropertyToNew(
				"spring.sleuth.log.slf4j.whitelisted-mdc-keys",
				collectFieldsFromProperty(whitelistedKeys),
				"spring.sleuth.baggage.correlation-fields",
				sleuthBaggageProperties.getCorrelationFields());

		CorrelationScopeDecorator.Builder builder = MDCScopeDecorator.newBuilder();
		for (String field : correlationFields) {
			builder.add(SingleCorrelationField.newBuilder(BaggageField.create(field))
					.build());
		}
		return builder.build();
	}

	static Set<String> collectFieldsFromProperty(String value) {
		Set<String> result = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		for (String fieldName : value.split(",")) {
			if (fieldName == null) {
				continue;
			}
			fieldName = fieldName.trim();
			if (fieldName.isEmpty()) {
				continue;
			}
			result.add(fieldName);
		}
		return result;
	}

}
