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

package org.springframework.cloud.sleuth.zipkin2;

import org.springframework.http.MediaType;

/**
 * Sends spans to Zipkin via an HTTP Client.
 *
 * @author Marcin Grzejszczak
 * @since 3.1.1
 */
interface ZipkinHttpClientSender {

	/**
	 * Sends spans to Zipkin via an HTTP Client.
	 * @param url Zipkin URL
	 * @param mediaType HTTP message media type
	 * @param payload payload to send
	 */
	void call(String url, MediaType mediaType, byte[] payload);

}
