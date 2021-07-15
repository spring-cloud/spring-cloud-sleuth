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

package org.springframework.cloud.sleuth.autoconfig.actuate;

import org.springframework.boot.actuate.endpoint.Producible;
import org.springframework.http.MediaType;
import org.springframework.util.MimeType;

/**
 * A {@link Producible} enum for supported span outputs.
 *
 * @author Marcin Grzejszczak
 * @since 3.1.0
 */
public enum TextOutputFormat implements Producible<TextOutputFormat> {

	/**
	 * OTLP protobuf format.
	 */
	CONTENT_TYPE_OTLP_PROTOBUF(MediaType.parseMediaType("application/x-protobuf")),

	/**
	 * OpenZipkin text.
	 */
	CONTENT_TYPE_OPENZIPKIN_JSON_V2(MediaType.APPLICATION_JSON);

	private final MimeType mimeType;

	TextOutputFormat(MimeType mimeType) {
		this.mimeType = mimeType;
	}

	@Override
	public MimeType getProducedMimeType() {
		return this.mimeType;
	}

}
