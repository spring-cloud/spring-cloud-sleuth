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

package org.springframework.cloud.sleuth.dynamic;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties of dynamic aspectJ tracing.
 *
 * @author Taras Danylchuk
 * @since 2.2.0
 */
@ConfigurationProperties("spring.sleuth.dynamic.tracing")
public class SleuthDynamicAspectJTracingProperties {

	private String expression;

	private boolean traceParameters;

	public String getExpression() {
		return expression;
	}

	public void setExpression(String expression) {
		this.expression = expression;
	}

	public boolean isTraceParameters() {
		return traceParameters;
	}

	public void setTraceParameters(boolean traceParameters) {
		this.traceParameters = traceParameters;
	}

}
