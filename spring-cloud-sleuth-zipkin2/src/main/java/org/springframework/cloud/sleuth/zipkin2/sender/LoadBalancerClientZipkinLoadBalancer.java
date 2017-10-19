package org.springframework.cloud.sleuth.zipkin2.sender;

import java.net.URI;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.sleuth.zipkin2.ZipkinLoadBalancer;
import org.springframework.cloud.sleuth.zipkin2.ZipkinProperties;

class LoadBalancerClientZipkinLoadBalancer implements ZipkinLoadBalancer {

	private final LoadBalancerClient loadBalancerClient;
	private final ZipkinProperties zipkinProperties;

	LoadBalancerClientZipkinLoadBalancer(LoadBalancerClient loadBalancerClient,
			ZipkinProperties zipkinProperties) {
		this.loadBalancerClient = loadBalancerClient;
		this.zipkinProperties = zipkinProperties;
	}

	@Override
	public URI instance() {
		if (this.loadBalancerClient != null) {
			URI uri = URI.create(this.zipkinProperties.getBaseUrl());
			String host = uri.getHost();
			ServiceInstance instance = this.loadBalancerClient.choose(host);
			if (instance != null) {
				return URI.create(String.format("http://%s:%s", instance.getHost(), instance.getPort()));
			}
		}
		return URI.create(this.zipkinProperties.getBaseUrl());
	}
}