/*
 * Copyright 2013-2015 the original author or authors.
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

package org.springframework.cloud.sleuth.zipkin;

import com.github.kristofa.brave.HttpSpanCollector;
import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * @author Spencer Gibb
 */
@ConfigurationProperties("spring.zipkin")
@Data
public class ZipkinProperties {
	// Sample rate = 1.0 means 100% of requests will get traced.
	private float fixedSampleRate = 1.0f;
	private String host = "localhost";
	private int port = 9411;
	private boolean enabled = true;
	private HttpSpanCollector.Config httpConfig = HttpSpanCollector.Config.builder().build();
}
