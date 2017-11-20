package org.springframework.cloud.sleuth.zipkin;

import static org.assertj.core.api.BDDAssertions.then;
import static org.springframework.cloud.sleuth.zipkin.ZipkinDiscoveryClientTests.ZIPKIN_RULE;

import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.awaitility.Awaitility;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.client.loadbalancer.LoadBalancerRequest;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanReporter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit4.SpringRunner;

import zipkin.junit.ZipkinRule;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ZipkinDiscoveryClientTests.Config.class,
		properties = "spring.zipkin.baseUrl=http://zipkin/")
public class ZipkinDiscoveryClientTests {

	@ClassRule public static ZipkinRule ZIPKIN_RULE = new ZipkinRule();

	@Autowired SpanReporter spanReporter;

	@Test
	public void shouldUseDiscoveryClientToFindZipkinUrlIfPresent() throws Exception {
		Span span = Span.builder().traceIdHigh(1L).traceId(2L).spanId(3L).name("foo")
				.build();

		this.spanReporter.report(span);

		Awaitility.await().untilAsserted(() -> then(ZIPKIN_RULE.httpRequestCount()).isGreaterThan(0));
	}

	@Configuration
	@EnableAutoConfiguration
	static class Config {

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
							return URI.create(ZIPKIN_RULE.httpUrl()).getPort();
						}

						@Override
						public boolean isSecure() {
							return false;
						}

						@Override
						public URI getUri() {
							return URI.create(ZIPKIN_RULE.httpUrl());
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