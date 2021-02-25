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

package org.springframework.cloud.sleuth.autoconfig.brave;

import java.util.Collections;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.sleuth.brave.propagation.PropagationType;

/**
 * Sleuth propagation settings.
 *
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
@ConfigurationProperties("spring.sleuth.propagation")
public class SleuthPropagationProperties {

	/**
	 * Tracing context propagation types.
	 */
	private List<PropagationType> type = Collections.singletonList(PropagationType.B3);

	public List<PropagationType> getType() {
		return this.type;
	}

	public void setType(List<PropagationType> type) {
		this.type = type;
	}

}
