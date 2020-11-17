/*
 * Copyright 2013-2020 the original author or authors.
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

package org.springframework.cloud.sleuth.brave.autoconfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import brave.Tags;
import brave.baggage.BaggageField;
import brave.baggage.BaggagePropagation;
import brave.baggage.BaggagePropagationConfig.SingleBaggageField;
import brave.baggage.BaggagePropagationCustomizer;
import brave.baggage.CorrelationScopeConfig.SingleCorrelationField;
import brave.baggage.CorrelationScopeCustomizer;
import brave.baggage.CorrelationScopeDecorator;
import brave.context.slf4j.MDCScopeDecorator;
import brave.handler.MutableSpan;
import brave.handler.SpanHandler;
import brave.propagation.B3Propagation;
import brave.propagation.CurrentTraceContext.ScopeDecorator;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.slf4j.MDC;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.sleuth.autoconfig.SleuthBaggageProperties;
import org.springframework.cloud.sleuth.brave.propagation.PropagationFactorySupplier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
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
class BraveBaggageConfiguration {

	static final Log logger = LogFactory.getLog(BraveBaggageConfiguration.class);

	static final String LOCAL_KEYS = "spring.sleuth.local-keys";
	static final String BAGGAGE_KEYS = "spring.sleuth.baggage-keys";
	static final String PROPAGATION_KEYS = "spring.sleuth.propagation-keys";
	static final String WHITELISTED_KEYS = "spring.sleuth.propagation.tag.whitelisted-keys";
	static final String WHITELISTED_MDC_KEYS = "spring.sleuth.log.slf4j.whitelisted-mdc-keys";

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

	@Bean(WHITELISTED_MDC_KEYS)
	@ConfigurationProperties(WHITELISTED_MDC_KEYS)
	List<String> whiteListedMDCKeys() {
		return new ArrayList<>();
	}

	// Note: Versions <2.2.3 use injectFormat(MULTI) for non-remote (ex spring-messaging)
	// See #1643
	@Bean
	@ConditionalOnMissingBean
	PropagationFactorySupplier defaultPropagationFactorySupplier() {
		return () -> B3Propagation.newFactoryBuilder().injectFormat(B3Propagation.Format.SINGLE_NO_PARENT).build();
	}

	/**
	 * To override the underlying context format, override this bean and set the delegate
	 * to what you need. {@link BaggagePropagation.FactoryBuilder} will unwrap itself if
	 * no fields are configured.
	 *
	 * <p>
	 * This will use {@link B3Propagation.Format#SINGLE_NO_PARENT} for non-remote spans,
	 * such as for messaging. Note: it will still parse incoming multi-header spans.
	 */
	@Bean
	@ConditionalOnMissingBean
	BaggagePropagation.FactoryBuilder baggagePropagationFactoryBuilder(PropagationFactorySupplier supplier) {
		return BaggagePropagation.newFactoryBuilder(supplier.get());
	}

	@Bean
	@ConditionalOnMissingBean
	Propagation.Factory sleuthPropagation(BaggagePropagation.FactoryBuilder factoryBuilder,
			@Qualifier(BAGGAGE_KEYS) List<String> baggageKeys, @Qualifier(LOCAL_KEYS) List<String> localKeys,
			@Qualifier(PROPAGATION_KEYS) List<String> propagationKeys, SleuthBaggageProperties sleuthBaggageProperties,
			@Nullable List<BaggagePropagationCustomizer> baggagePropagationCustomizers) {

		Set<String> localFields = redirectOldPropertyToNew(LOCAL_KEYS, localKeys, "spring.sleuth.baggage.local-fields",
				sleuthBaggageProperties.getLocalFields());
		for (String fieldName : localFields) {
			factoryBuilder.add(SingleBaggageField.local(BaggageField.create(fieldName)));
		}

		Set<String> remoteFields = redirectOldPropertyToNew(PROPAGATION_KEYS, propagationKeys,
				"spring.sleuth.baggage.remote-fields", sleuthBaggageProperties.getRemoteFields());
		for (String fieldName : remoteFields) {
			factoryBuilder.add(SingleBaggageField.remote(BaggageField.create(fieldName)));
		}

		if (!baggageKeys.isEmpty()) {
			logger.warn("'" + BAGGAGE_KEYS + "' will be removed in a future release.\n"
					+ "To change header names define a @Bean of type " + SingleBaggageField.class.getName());

			for (String key : baggageKeys) {
				factoryBuilder.add(SingleBaggageField.newBuilder(BaggageField.create(key)).addKeyName("baggage-" + key) // for
																														// HTTP
						.addKeyName("baggage_" + key) // for messaging
						.build());
			}
		}

		if (baggagePropagationCustomizers != null) {
			for (BaggagePropagationCustomizer customizer : baggagePropagationCustomizers) {
				customizer.customize(factoryBuilder);
			}
		}
		return factoryBuilder.build();
	}

	static Set<String> redirectOldPropertyToNew(String oldProperty, List<String> oldValue, String newProperty,
			List<String> newValue) {
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
	@ConditionalOnProperty(value = "spring.sleuth.baggage.correlation-enabled", matchIfMissing = true)
	ScopeDecorator correlationScopeDecorator(@Qualifier(WHITELISTED_MDC_KEYS) List<String> whiteListedMDCKeys,
			SleuthBaggageProperties sleuthBaggageProperties,
			@Nullable List<CorrelationScopeCustomizer> correlationScopeCustomizers) {

		Set<String> correlationFields = redirectOldPropertyToNew(WHITELISTED_MDC_KEYS, whiteListedMDCKeys,
				"spring.sleuth.baggage.correlation-fields", sleuthBaggageProperties.getCorrelationFields());

		// Add fields from properties
		CorrelationScopeDecorator.Builder builder = MDCScopeDecorator.newBuilder();
		for (String field : correlationFields) {
			builder.add(SingleCorrelationField.newBuilder(BaggageField.create(field)).build());
		}

		// handle user overrides
		if (correlationScopeCustomizers != null) {
			for (CorrelationScopeCustomizer customizer : correlationScopeCustomizers) {
				customizer.customize(builder);
			}
		}
		return builder.build();
	}

	/**
	 * This has to be conditional as it creates a bean of type {@link SpanHandler}.
	 *
	 * <p>
	 * {@link SpanHandler} beans, even if {@link SpanHandler#NOOP}, can trigger
	 * {@code org.springframework.cloud.sleuth.brave.sampler.SamplerCondition}
	 */
	@Configuration(proxyBeanMethods = false)
	@Conditional(BaggageTagSpanHandlerCondition.class)
	static class BaggageTagSpanHandlerConfiguration {

		@Bean(WHITELISTED_KEYS)
		@ConfigurationProperties(WHITELISTED_KEYS)
		List<String> whiteListedKeys() {
			return new ArrayList<>();
		}

		@Bean
		SpanHandler baggageTagSpanHandler(@Qualifier(WHITELISTED_KEYS) List<String> whiteListedKeys,
				SleuthBaggageProperties sleuthBaggageProperties) {

			Set<String> tagFields = redirectOldPropertyToNew(WHITELISTED_KEYS, whiteListedKeys,
					"spring.sleuth.baggage.tag-fields", sleuthBaggageProperties.getTagFields());

			if (tagFields.isEmpty()) {
				return SpanHandler.NOOP; // Brave ignores these
			}

			return new BaggageTagSpanHandler(tagFields.stream().map(BaggageField::create).toArray(BaggageField[]::new));
		}

	}

	/**
	 * We need a special condition as it users could use either comma or yaml encoding,
	 * possibly with a deprecated prefix.
	 */
	static class BaggageTagSpanHandlerCondition extends AnyNestedCondition {

		BaggageTagSpanHandlerCondition() {
			super(ConfigurationPhase.PARSE_CONFIGURATION);
		}

		@ConditionalOnProperty("spring.sleuth.baggage.tag-fields")
		static class TagFieldsProperty {

		}

		@ConditionalOnProperty("spring.sleuth.baggage.tag-fields[0]")
		static class TagFieldsYamlListProperty {

		}

		@ConditionalOnProperty(WHITELISTED_KEYS)
		static class WhitelistedKeysProperty {

		}

		@ConditionalOnProperty(WHITELISTED_KEYS + "[0]")
		static class WhitelistedKeysYamlListProperty {

		}

	}

	static final class BaggageTagSpanHandler extends SpanHandler {

		final BaggageField[] fieldsToTag;

		BaggageTagSpanHandler(BaggageField[] fieldsToTag) {
			this.fieldsToTag = fieldsToTag;
		}

		@Override
		public boolean end(TraceContext context, MutableSpan span, Cause cause) {
			for (BaggageField field : fieldsToTag) {
				Tags.BAGGAGE_FIELD.tag(field, context, span);
			}
			return true;
		}

	}

}
