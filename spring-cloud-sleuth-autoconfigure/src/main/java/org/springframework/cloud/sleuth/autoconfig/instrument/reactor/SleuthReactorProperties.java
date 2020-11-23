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

package org.springframework.cloud.sleuth.autoconfig.instrument.reactor;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Sleuth Reactor settings.
 *
 * @author Marcin Grzejszczak
 * @since 2.0.2
 */
@ConfigurationProperties("spring.sleuth.reactor")
public class SleuthReactorProperties {

	private static final Log log = LogFactory.getLog(SleuthReactorProperties.class);

	/**
	 * When true enables instrumentation for reactor.
	 */
	private boolean enabled = true;

	/**
	 * When true decorates on each operator, will be less performing, but logging will
	 * always contain the tracing entries in each operator. When false decorates on last
	 * operator, will be more performing, but logging might not always contain the tracing
	 * entries.
	 * @deprecated use explicit value via
	 * {@link SleuthReactorProperties#instrumentationType}
	 */
	@Deprecated
	private boolean decorateOnEach = true;

	private InstrumentationType instrumentationType = InstrumentationType.DECORATE_ON_EACH;

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	@Deprecated
	public boolean isDecorateOnEach() {
		warn();
		return this.instrumentationType == InstrumentationType.DECORATE_ON_EACH;
	}

	@Deprecated
	public void setDecorateOnEach(boolean decorateOnEach) {
		warn();
		this.instrumentationType = decorateOnEach ? InstrumentationType.DECORATE_ON_EACH
				: InstrumentationType.DECORATE_ON_LAST;
	}

	private void warn() {
		log.warn(
				"You're using the deprecated [spring.sleuth.reactor.decorate-on-each] property. Please use the [spring.sleuth.reactor.instrumentation-type]");
	}

	public InstrumentationType getInstrumentationType() {
		return this.instrumentationType;
	}

	public void setInstrumentationType(InstrumentationType instrumentationType) {
		this.instrumentationType = instrumentationType;
	}

	public enum InstrumentationType {

		/**
		 * Wraps each operator in a Sleuth representation.
		 */
		DECORATE_ON_EACH,

		/**
		 * Wraps only the last operator in Sleuth representation.
		 */
		DECORATE_ON_LAST,

		/**
		 * Does not automatically wrap any operators.
		 */
		MANUAL;

	}

}
