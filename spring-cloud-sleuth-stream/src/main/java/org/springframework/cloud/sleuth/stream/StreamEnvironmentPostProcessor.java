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
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.cloud.sleuth.TraceHeaders;
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
 * Sleuth Stream.
 *
 * If you're changing the {@link TraceHeaders} default values you have to override
 * the appropriate {@code spring.cloud.stream.binder} header values.
 *
 * @author Dave Syer
 *
 * @since 1.0.0
 */
public class StreamEnvironmentPostProcessor implements EnvironmentPostProcessor {

	private static final String PROPERTY_SOURCE_NAME = "defaultProperties";
	private static String[] headers = new String[] { TraceHeaders.ZIPKIN_SPAN_ID_HEADER_NAME,
			TraceHeaders.ZIPKIN_TRACE_ID_HEADER_NAME,
			TraceHeaders.ZIPKIN_PARENT_SPAN_ID_HEADER_NAME,
			TraceHeaders.ZIPKIN_PROCESS_ID_HEADER_NAME,
			TraceHeaders.Sleuth.SLEUTH_EXPORTABLE_HEADER_NAME,
			TraceHeaders.Sleuth.SLEUTH_SPAN_NAME_HEADER_NAME };

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
					addHeaders(map, binderType);
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
		addOrReplace(environment.getPropertySources(), map);
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

	private void addHeaders(Map<String, Object> map, String binder) {
		String stem = "spring.cloud.stream.binder." + binder + ".headers";
		for (int i = 0; i < headers.length; i++) {
			map.put(stem + "[" + i + "]", headers[i]);
		}
	}

}
