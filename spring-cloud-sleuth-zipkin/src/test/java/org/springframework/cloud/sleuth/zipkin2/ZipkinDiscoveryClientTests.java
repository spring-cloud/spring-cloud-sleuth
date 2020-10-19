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

import java.io.IOException;
import java.net.URI;
import java.util.Map;

import okhttp3.mockwebserver.MockWebServer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.client.loadbalancer.LoadBalancerRequest;
import org.springframework.cloud.client.loadbalancer.Request;
import org.springframework.cloud.sleuth.api.Span;
import org.springframework.cloud.sleuth.api.Tracer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.BDDAssertions.then;

@ContextConfiguration(classes = ZipkinDiscoveryClientTests.Config.class)
@TestPropertySource(properties = { "spring.zipkin.baseUrl=https://zipkin/", "spring.zipkin.sender.type=web" })
public abstract class ZipkinDiscoveryClientTests {

	@Autowired
	MockWebServer mockWebServer;

	@Autowired
	Tracer tracer;

	@Test
	public void shouldUseDiscoveryClientToFindZipkinUrlIfPresent() throws Exception {
		Span span = this.tracer.nextSpan().name("foo").start();

		span.end();

		Awaitility.await().untilAsserted(() -> then(mockWebServer.getRequestCount()).isGreaterThan(0));
	}

	@Configuration(proxyBeanMethods = false)
	@EnableAutoConfiguration
	static class Config {

		@Bean(initMethod = "start", destroyMethod = "close")
		MockWebServer mockWebServer() {
			return new MockWebServer();
		}

		@Bean
		LoadBalancerClient loadBalancerClient(MockWebServer mockWebServer) {
			return new LoadBalancerClient() {
				@Override
				public <T> T execute(String serviceId, LoadBalancerRequest<T> request) throws IOException {
					return null;
				}

				@Override
				public <T> T execute(String serviceId, ServiceInstance serviceInstance, LoadBalancerRequest<T> request)
						throws IOException {
					return null;
				}

				@Override
				public URI reconstructURI(ServiceInstance instance, URI original) {
					return null;
				}

				@Override
				public ServiceInstance choose(String serviceId) {
					return instance();
				}

				private ServiceInstance instance() {
					return new ServiceInstance() {
						@Override
						public String getServiceId() {
							return "zipkin";
						}

						@Override
						public String getHost() {
							return "localhost";
						}

						@Override
						public int getPort() {
							return mockWebServer.url("/").port();
						}

						@Override
						public boolean isSecure() {
							return false;
						}

						@Override
						public URI getUri() {
							return mockWebServer.url("/").uri();
						}

						@Override
						public Map<String, String> getMetadata() {
							return null;
						}
					};
				}

				@Override
				public <T> ServiceInstance choose(String serviceId, Request<T> request) {
					return instance();
				}
			};
		}

	}

}
