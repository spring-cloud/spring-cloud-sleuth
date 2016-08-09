/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.sleuth.stream;

import java.io.IOException;
import java.util.AbstractMap.SimpleEntry;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.cloud.sleuth.Span;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.util.StringUtils;

/**
 * {@link EnvironmentPostProcessor} that sets the default properties for
 * Sleuth Stream.
 *
 * @author Dave Syer
 * @since 1.0.0
 */
public class StreamEnvironmentPostProcessor implements EnvironmentPostProcessor {

	private static final String PROPERTY_SOURCE_NAME = "defaultProperties";
	private static final String HEADER_PREFIX = "spring.sleuth.integration.headers";
	// Tuples containing the default header value (key) and the header name (value)
	private static SimpleEntry[] DEFAULT_HEADERS = new SimpleEntry[] {
		new SimpleEntry<String, String>(Span.SPAN_ID_NAME, "spanId"),
		new SimpleEntry<String, String>(Span.TRACE_ID_NAME, "traceId"),
		new SimpleEntry<String, String>(Span.PARENT_ID_NAME, "parentId"),
		new SimpleEntry<String, String>(Span.PROCESS_ID_NAME, null),
		new SimpleEntry<String, String>(Span.SAMPLED_NAME, "sampled"),
		new SimpleEntry<String, String>(Span.SPAN_NAME_NAME, null)};

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
			for (Resource resource : resolver
					.getResources("classpath*:META-INF/spring.binders")) {
				for (String binderType : parseBinderConfigurations(resource)) {
					int startIndex = findStartIndex(environment, binderType);
					addHeaders(map, binderType, startIndex, environment);
				}
			}
		}
		catch (IOException e) {
			throw new IllegalStateException("Cannot load META-INF/spring.binders", e);
		}
		// Technically this is only needed on the consumer, but it's fine to be explicit
		// on producers as well. It puts all consumers in the same "group", meaning they
		// compete with each other and only one gets each message.
		map.put("spring.cloud.stream.bindings." + SleuthSink.INPUT + ".group",
				environment.getProperty("spring.sleuth.stream.group", SleuthSink.INPUT));
		map.put("spring.cloud.stream.bindings." + SleuthSink.INPUT + ".content-type",
				environment.getProperty("spring.sleuth.stream.content-type", "application/json"));
		addOrReplace(environment.getPropertySources(), map);
	}

	private int findStartIndex(ConfigurableEnvironment environment, String binder) {
		String prefix = "spring.cloud.stream." + binder + ".binder.headers";
		int i = 0;
		while (environment.getProperty(prefix + "[" + i + "]")!=null) {
			i++;
		}
		return i;
	}

	private Collection<String> parseBinderConfigurations(Resource resource) {
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

	private void addHeaders(Map<String, Object> map, String binder, int startIndex, Environment environment) {
		String stem = "spring.cloud.stream." + binder + ".binder.headers";
		for (int i = 0; i < DEFAULT_HEADERS.length; i++) {
			map.put(stem + "[" + (i + startIndex) + "]", headerValue(DEFAULT_HEADERS[i], environment));
		}
	}

	private String headerValue(SimpleEntry<String, String> tuple, Environment environment) {
		if (tuple.getValue() != null) {
			String property = environment.getProperty(HEADER_PREFIX + "." + tuple.getValue());
			if (StringUtils.hasText(property)) {
				return property;
			}
		}
		return tuple.getKey();
	}

}
