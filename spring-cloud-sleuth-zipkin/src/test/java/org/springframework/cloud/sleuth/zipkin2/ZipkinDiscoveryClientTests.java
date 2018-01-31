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

import java.io.IOException;
import java.net.URI;
import java.util.Map;

import brave.Span;
import brave.Tracing;
import brave.sampler.Sampler;
import okhttp3.mockwebserver.MockWebServer;
import org.awaitility.Awaitility;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.client.loadbalancer.LoadBalancerRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.BDDAssertions.then;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ZipkinDiscoveryClientTests.Config.class, properties = {
		"spring.zipkin.baseUrl=http://zipkin/",
		"spring.zipkin.sender.type=web" // override default priority which picks rabbit due to classpath
})
public class ZipkinDiscoveryClientTests {

	@ClassRule public static MockWebServer ZIPKIN_RULE = new MockWebServer();

	@Autowired Tracing tracing;

	@Test
	public void shouldUseDiscoveryClientToFindZipkinUrlIfPresent() throws Exception {
		Span span = this.tracing.tracer().nextSpan().name("foo").start();

		span.finish();

		Awaitility.await().untilAsserted(() -> then(ZIPKIN_RULE.getRequestCount()).isGreaterThan(0));
	}

	@Configuration
	@EnableAutoConfiguration
	static class Config {

		@Bean Sampler sampler() {
			return Sampler.ALWAYS_SAMPLE;
		}

		@Bean LoadBalancerClient loadBalancerClient() {
			return new LoadBalancerClient() {
				@Override public <T> T execute(String serviceId,
						LoadBalancerRequest<T> request) throws IOException {
					return null;
				}

				@Override public <T> T execute(String serviceId,
						ServiceInstance serviceInstance, LoadBalancerRequest<T> request)
						throws IOException {
					return null;
				}

				@Override public URI reconstructURI(ServiceInstance instance,
						URI original) {
					return null;
				}

				@Override public ServiceInstance choose(String serviceId) {
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
							return ZIPKIN_RULE.url("/").port();
						}

						@Override
						public boolean isSecure() {
							return false;
						}

						@Override
						public URI getUri() {
							return ZIPKIN_RULE.url("/").uri();
						}

						@Override
						public Map<String, String> getMetadata() {
							return null;
						}
					};
				}
			};
		}
	}
}