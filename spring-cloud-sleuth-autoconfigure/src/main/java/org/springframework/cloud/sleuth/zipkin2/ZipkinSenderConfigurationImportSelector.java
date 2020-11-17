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

package org.springframework.cloud.sleuth.zipkin2;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.context.annotation.ImportSelector;
import org.springframework.core.type.AnnotationMetadata;

/**
 * Import selector depending on the sender type.
 *
 * @author Adrian Cole
 */
class ZipkinSenderConfigurationImportSelector implements ImportSelector {

	static final Map<String, String> MAPPINGS;

	// Classes below must be annotated with @Conditional(ZipkinSenderCondition.class)
	static {
		// Mappings in descending priority (highest is last)
		Map<String, String> mappings = new LinkedHashMap<>();
		mappings.put("activemq", ZipkinActiveMqSenderConfiguration.class.getName());
		mappings.put("rabbit", ZipkinRabbitSenderConfiguration.class.getName());
		mappings.put("kafka", ZipkinKafkaSenderConfiguration.class.getName());
		mappings.put("web", ZipkinRestTemplateSenderConfiguration.class.getName());
		MAPPINGS = Collections.unmodifiableMap(mappings);
	}

	static String getType(String configurationClassName) {
		for (Map.Entry<String, String> entry : MAPPINGS.entrySet()) {
			if (entry.getValue().equals(configurationClassName)) {
				return entry.getKey();
			}
		}
		throw new IllegalStateException("Unknown configuration class " + configurationClassName);
	}

	@Override
	public String[] selectImports(AnnotationMetadata importingClassMetadata) {
		return MAPPINGS.values().toArray(new String[0]);
	}

}
