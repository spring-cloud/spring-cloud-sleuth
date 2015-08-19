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

package org.springframework.cloud.sleuth.bootstrap;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.cloud.sleuth.Trace;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.ClassUtils;

/**
 * @author Dave Syer
 *
 */
public class TraceBootstrapEnvironmentPostProcessor implements EnvironmentPostProcessor {

	private static final String STREAM_BINDER_HEADERS = "streamBinderHeaders";
	private static String[] headers = new String[] { Trace.SPAN_ID_NAME,
		Trace.TRACE_ID_NAME, Trace.PARENT_ID_NAME, Trace.PROCESS_ID_NAME,
		Trace.NOT_SAMPLED_NAME, Trace.SPAN_NAME_NAME };

	@Override
	public void postProcessEnvironment(ConfigurableEnvironment environment,
			SpringApplication application) {
		Map<String, Object> map = new HashMap<String, Object>();
		addHeaders(
				map,
				"org.springframework.cloud.stream.binder.redis.RedisMessageChannelBinder",
				"redis");
		addHeaders(
				map,
				"org.springframework.cloud.stream.binder.rabbit.RabbitMessageChannelBinder",
				"rabbit");
		addHeaders(
				map,
				"org.springframework.cloud.stream.binder.kafka.KafkaMessageChannelBinder",
				"kafka");
		MapPropertySource source = new MapPropertySource(STREAM_BINDER_HEADERS, map);
		environment.getPropertySources().addLast(source);
	}

	private void addHeaders(Map<String, Object> map, String type, String binder) {
		if (ClassUtils.isPresent(type, null)) {
			String stem = "spring.cloud.stream.binder." + binder + ".headers";
			for (int i = 0; i < headers.length; i++) {
				map.put(stem + "[" + i + "]", headers[i]);
			}
		}

	}

}
