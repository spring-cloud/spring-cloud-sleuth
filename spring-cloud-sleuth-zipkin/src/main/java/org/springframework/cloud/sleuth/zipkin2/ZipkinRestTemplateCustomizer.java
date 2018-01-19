/*
 * Copyright 2013-2018 the original author or authors.
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

package org.springframework.cloud.sleuth.zipkin2;

import org.springframework.web.client.RestTemplate;

/**
 * Implementations customize the {@link RestTemplate} used to report spans to Zipkin.
 * For example, they can add an additional header needed by their environment.
 *
 * <p>Implementors must gzip according to {@link ZipkinProperties.Compression},
 * for example by using the {@link DefaultZipkinRestTemplateCustomizer}.
 *
 * @author Marcin Grzejszczak
 *
 * @since 1.1.0
 */
public interface ZipkinRestTemplateCustomizer {

	void customize(RestTemplate restTemplate);
}
