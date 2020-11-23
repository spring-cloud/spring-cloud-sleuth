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

import java.net.URI;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * {@link ZipkinUrlExtractor} with caching mechanism.
 *
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
public class CachingZipkinUrlExtractor implements ZipkinUrlExtractor {

	private static final Log log = LogFactory.getLog(CachingZipkinUrlExtractor.class);

	final AtomicInteger zipkinPort = new AtomicInteger();

	private final ZipkinLoadBalancer zipkinLoadBalancer;

	public CachingZipkinUrlExtractor(ZipkinLoadBalancer zipkinLoadBalancer) {
		this.zipkinLoadBalancer = zipkinLoadBalancer;
	}

	@Override
	public URI zipkinUrl(ZipkinProperties zipkinProperties) {
		int cachedZipkinPort = zipkinPort(zipkinProperties);
		if (cachedZipkinPort == -1) {
			if (log.isDebugEnabled()) {
				log.debug("The port in Zipkin's URL [" + zipkinProperties.getBaseUrl()
						+ "] wasn't provided - that means that load balancing might take place");
			}
			return this.zipkinLoadBalancer.instance();
		}
		if (log.isDebugEnabled()) {
			log.debug("The port in Zipkin's URL [" + zipkinProperties.getBaseUrl()
					+ "] is provided - that means that load balancing will not take place");
		}
		return noOpZipkinLoadBalancer(zipkinProperties).instance();
	}

	StaticInstanceZipkinLoadBalancer noOpZipkinLoadBalancer(ZipkinProperties zipkinProperties) {
		return new StaticInstanceZipkinLoadBalancer(zipkinProperties);
	}

	private int zipkinPort(ZipkinProperties zipkinProperties) {
		int cachedZipkinPort = this.zipkinPort.get();
		if (cachedZipkinPort != 0) {
			return cachedZipkinPort;
		}
		return calculatePort(zipkinProperties);
	}

	int calculatePort(ZipkinProperties zipkinProperties) {
		String baseUrl = zipkinProperties.getBaseUrl();
		URI uri = createUri(baseUrl);
		int zipkinPort = uri.getPort();
		this.zipkinPort.set(zipkinPort);
		return zipkinPort;
	}

	URI createUri(String baseUrl) {
		return URI.create(baseUrl);
	}

}
