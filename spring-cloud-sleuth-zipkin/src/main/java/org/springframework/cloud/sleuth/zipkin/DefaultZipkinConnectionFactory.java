/*
 * Copyright 2013-2016 the original author or authors.
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

import java.io.IOException;
import java.net.HttpURLConnection;

import org.springframework.http.client.SimpleClientHttpRequestFactory;

/**
 * Default ConnectionFactory for the requests sent to Zipkin. Adds GZip compression
 * if a property is set in {@link ZipkinProperties}.
 *
 * @author Marcin Grzejszczak
 *
 * @since 1.1.0
 */
class DefaultZipkinConnectionFactory extends SimpleClientHttpRequestFactory {

	private final ZipkinProperties zipkinProperties;

	public DefaultZipkinConnectionFactory(ZipkinProperties zipkinProperties) {
		this.zipkinProperties = zipkinProperties;
		setBufferRequestBody(false);
	}

	@Override
	protected void prepareConnection(HttpURLConnection connection, String httpMethod)
			throws IOException {
		super.prepareConnection(connection, httpMethod);
		if (this.zipkinProperties.getCompression().isEnabled()) {
			connection.addRequestProperty("Content-Encoding", "gzip");
		}
	}
}