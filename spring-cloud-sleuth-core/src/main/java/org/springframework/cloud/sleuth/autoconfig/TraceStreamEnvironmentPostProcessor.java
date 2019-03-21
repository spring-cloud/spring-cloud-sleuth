/*
 * Copyright 2013-2018 the original author or authors.
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.cloud.sleuth.instrument.messaging.TraceMessageHeaders;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.PropertiesLoaderUtils;

/**
 * {@link EnvironmentPostProcessor} that sets the default properties for
 * Sleuth Stream. Since the sleuth-stream module gets deprecated, we've
 * copied the module to core with small adjustments to ensure that the
 * tracing headers are copied. That's required for the Kafka binder.
 *
 * @author Dave Syer
 * @since 1.3.5
 */
class TraceStreamEnvironmentPostProcessor implements EnvironmentPostProcessor {

	private static final String PROPERTY_SOURCE_NAME = "defaultProperties";
	static final String[] HEADERS = new String[] { TraceMessageHeaders.SPAN_ID_NAME,
			TraceMessageHeaders.TRACE_ID_NAME, TraceMessageHeaders.PARENT_ID_NAME, TraceMessageHeaders.PROCESS_ID_NAME,
			TraceMessageHeaders.SAMPLED_NAME, TraceMessageHeaders.SPAN_NAME_NAME };

	@Override
	public void postProcessEnvironment(ConfigurableEnvironment environment,
			SpringApplication application) {
		Map<String, Object> map = new HashMap<String, Object>();
		ResourceLoader resourceLoader = application.getResourceLoader();
		resourceLoader = resourceLoader == null ? new DefaultResourceLoader()
				: resourceLoader;
		PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(
				resourceLoader);
		try {
			for (Resource resource : getAllSpringBinders(resolver)) {
				for (String binderType : parseBinderConfigurations(resource)) {
					List<String> existingHeaders = existingHeaders(environment, binderType);
					int startIndex = findStartIndex(environment, binderType);
					startIndex = startIndex + existingHeaders.size();
					addHeaders(map, environment.getPropertySources(), binderType, startIndex, existingHeaders);
				}
			}
		}
		catch (IOException e) {
			throw new IllegalStateException("Cannot load META-INF/spring.binders", e);
		}
		addOrReplace(environment.getPropertySources(), map);
	}

	Resource[] getAllSpringBinders(PathMatchingResourcePatternResolver resolver)
			throws IOException {
		return resolver
				.getResources("classpath*:META-INF/spring.binders");
	}

	private List<String> existingHeaders(ConfigurableEnvironment environment, String binder) {
		String prefix = "spring.cloud.stream." + binder + ".binder.headers";
		String oldHeaders = environment.getProperty(prefix);
		if (oldHeaders != null) {
			return Arrays.asList(oldHeaders.split(","));
		}
		return new ArrayList<>();
	}

	private int findStartIndex(ConfigurableEnvironment environment, String binder) {
		String prefix = "spring.cloud.stream." + binder + ".binder.HEADERS";
		int i = 0;
		String oldHeaders = environment.getProperty(prefix);
		if (oldHeaders != null) {
			i = oldHeaders.split(",").length;
		}
		while (environment.getProperty(prefix + "[" + i + "]") != null) {
			i++;
		}
		return i;
	}

	Collection<String> parseBinderConfigurations(Resource resource) {
		Collection<String> keys = new HashSet<>();
		try {
			Properties props = PropertiesLoaderUtils.loadProperties(resource);
			for (Object object : props.keySet()) {
				keys.add(object.toString());
			}
		}
		catch (IOException e) {
		}
		return keys;
	}

	private void addOrReplace(MutablePropertySources propertySources,
			Map<String, Object> map) {
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

	private void addHeaders(Map<String, Object> map, MutablePropertySources propertySources,
			String binder, int startIndex, List<String> existingHeaders) {
		String stem = "spring.cloud.stream." + binder + ".binder.HEADERS";
		for (int i = 0; i < existingHeaders.size(); i++) {
			String header = existingHeaders.get(i);
			if (!hasHeaderKey(propertySources, header)) {
				putHeader(map, stem, i, header);
			}
		}
		for (int i = 0; i < HEADERS.length; i++) {
			boolean hasHeader = hasHeaderKey(propertySources, HEADERS[i]);
			if (!hasHeader) {
				putHeader(map, stem, i + startIndex, HEADERS[i]);
			} else if (!existingHeaders.isEmpty() && hasHeader) {
				removeEntryWithHeader(propertySources, HEADERS[i]);
				putHeader(map, stem, i + startIndex, HEADERS[i]);
			}
		}
	}

	private void putHeader(Map<String, Object> map, String stem, int i2, String header2) {
		map.put(stem + "[" + (i2) + "]", header2);
	}

	private boolean hasHeaderKey(MutablePropertySources propertySources, String header) {
		PropertySource<?> source = propertySources.get(PROPERTY_SOURCE_NAME);
		if (source instanceof MapPropertySource) {
			Collection<Object> values = ((MapPropertySource) source).getSource().values();
			return values.contains(header);
		}
		return false;
	}

	private void removeEntryWithHeader(MutablePropertySources propertySources, String header) {
		PropertySource<?> source = propertySources.get(PROPERTY_SOURCE_NAME);
		if (source instanceof MapPropertySource) {
			Collection<Object> values = ((MapPropertySource) source).getSource().values();
			if (values.contains(header)) {
				values.remove(header);
			}
		}
	}

}
