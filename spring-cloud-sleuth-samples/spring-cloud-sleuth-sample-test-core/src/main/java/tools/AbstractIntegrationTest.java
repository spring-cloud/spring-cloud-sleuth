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
package tools;

import com.github.kristofa.brave.SpanCollector;
import com.github.kristofa.brave.scribe.ScribeSpanCollector;
import com.jayway.awaitility.Awaitility;
import com.jayway.awaitility.core.ConditionFactory;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.sleuth.zipkin.ZipkinProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.*;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.net.URI;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author Marcin Grzejszczak
 */
@Slf4j
public abstract class AbstractIntegrationTest {

	protected static int pollInterval = 1;
	protected static int timeout = 120;
	protected RestTemplate restTemplate = new AssertingRestTemplate();

	protected static ConditionFactory await() {
		return Awaitility.await().pollInterval(pollInterval, SECONDS).atMost(timeout, SECONDS);
	}

	protected long zipkinHashedTraceId(String string) {
		long h = 1125899906842597L;
		if (string == null) {
			return h;
		}
		int len = string.length();

		for (int i = 0; i < len; i++) {
			h = 31 * h + string.charAt(i);
		}
		return h;
	}

	String zipkinHashedHexStringTraceId(String traceId) {
		long hashedTraceId = zipkinHashedTraceId(traceId);
		return Long.toHexString(hashedTraceId);
	}

	protected static String getDockerUrl() {
		URI dockerUri = getDockerURI();
		if (StringUtils.isEmpty(dockerUri.getScheme())) {
			return "http://localhost";
		}
		return "http://" + dockerUri.getHost();
	}

	protected static URI getDockerURI() {
		String dockerHost = System.getenv("DOCKER_HOST");
		if (StringUtils.isEmpty(dockerHost)) {
			return URI.create("http://localhost");
		}
		return URI.create(dockerHost);
	}

	protected Runnable zipkinQueryServerIsUp() {
		return new Runnable() {
			@Override
			public void run() {
				ResponseEntity<String> response = endpointToCheckZipkinQueryHealth();
				log.info("Response from the Zipkin query with current traces [{}]", response);
				then(response.getStatusCode()).isEqualTo(HttpStatus.OK);
				log.info("Zipkin query server is up!");
			}
		};
	}

	protected Runnable zipkinCollectorServerIsUp() {
		return new Runnable() {
			@Override
			public void run() {
				ResponseEntity<String> response = endpointToCheckZipkinCollectorHealth();
				log.info("Response from the Zipkin collector's health endpoint is [{}]", response);
				then(response.getStatusCode()).isEqualTo(HttpStatus.OK);
				log.info("Zipkin collector server is up!");
			}
		};
	}

	protected ResponseEntity<String> endpointToCheckZipkinQueryHealth() {
		URI uri = URI.create(getZipkinServicesQueryUrl());
		log.info("Sending request to the Zipkin query service [{}]", uri);
		return exchangeRequest(uri);
	}

	protected ResponseEntity<String> endpointToCheckZipkinCollectorHealth() {
		URI uri = URI.create(getZipkinCollectorHealthUrl());
		log.info("Sending request to the Zipkin collector service [{}]", uri);
		return exchangeRequest(uri);
	}

	protected ResponseEntity<String> checkStateOfTheTraceId(String traceId) {
		String hexTraceId = zipkinHashedHexStringTraceId(traceId);
		URI uri = URI.create(getZipkinTraceQueryUrl() + hexTraceId);
		log.info("Sending request to the Zipkin query service [{}]. Checking presence of trace id [{}] and its hex version [{}]", uri, traceId, hexTraceId);
		return exchangeRequest(uri);
	}

	protected ResponseEntity<String> exchangeRequest(URI uri) {
		return restTemplate.exchange(
				new RequestEntity<>(new HttpHeaders(), HttpMethod.GET, uri), String.class
		);
	}

	protected String getZipkinTraceQueryUrl() {
		return getDockerUrl() + ":9411/api/v1/trace/";
	}

	protected String getZipkinServicesQueryUrl() {
		return getDockerUrl() + ":9411/api/v1/services";
	}

	protected String getZipkinCollectorHealthUrl() {
		return getDockerUrl() + ":9900/health";
	}

	@Configuration
	public static class Config {
		@Bean
		SpanCollector integrationTestSpanCollector() {
			return new IntegrationTestSpanCollector();
		}
	}

	@Configuration
	@Slf4j
	public static class ZipkinConfig {
		@Bean
		@SneakyThrows
		public ScribeSpanCollector spanCollector(final ZipkinProperties zipkin) {
			await().until(new Runnable() {
				@Override
				public void run() {
					try {
						ZipkinConfig.this.getSpanCollector(zipkin);
					} catch (Exception e) {
						log.error("Exception occurred while trying to connect to zipkin [" + e.getCause() + "]");
						throw new AssertionError(e);
					}
				}
			});
			return getSpanCollector(zipkin);
		}

		private ScribeSpanCollector getSpanCollector(ZipkinProperties zipkin) {
			return new ScribeSpanCollector(getDockerURI().getHost(),
					zipkin.getPort(), zipkin.getCollector());
		}
	}
}
