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

package org.springframework.cloud.sleuth.instrument.deployer;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.cloud.deployer.spi.app.AppDeployer;
import org.springframework.cloud.deployer.spi.app.AppScaleRequest;
import org.springframework.cloud.deployer.spi.app.AppStatus;
import org.springframework.cloud.deployer.spi.core.AppDefinition;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.spi.core.RuntimeEnvironmentInfo;
import org.springframework.cloud.sleuth.tracer.SimpleCurrentTraceContext;
import org.springframework.cloud.sleuth.tracer.SimpleTracer;
import org.springframework.core.env.Environment;
import org.springframework.core.io.PathResource;
import org.springframework.mock.env.MockEnvironment;

class TraceAppDeployerTests {

	SimpleTracer simpleTracer = new SimpleTracer();

	AppDeployer delegate = BDDMockito.mock(AppDeployer.class);

	TraceAppDeployer traceAppDeployer = new TraceAppDeployer(this.delegate, beanFactory(), environment());

	@BeforeEach
	void setup() {
		BDDMockito.given(this.delegate.environmentInfo())
				.willReturn(new RuntimeEnvironmentInfo.Builder().spiClass(Object.class).implementationName("asd")
						.implementationVersion("asd").platformType("asd").platformApiVersion("asd")
						.platformClientVersion("asd").platformHostVersion("asd").build());
	}

	@Test
	void should_trace_deploy() {
		BDDMockito.given(this.delegate.statusReactive(BDDMockito.any()))
				.willReturn(Mono.just(AppStatus.of("asd").build()));

		this.traceAppDeployer.deploy(deploymentRequest());

		BDDAssertions.then(this.simpleTracer.getOnlySpan().tags).isNotEmpty();
		BDDMockito.then(this.delegate).should().deploy(BDDMockito.any());
	}

	@Test
	void should_trace_undeploy() {
		BDDMockito.given(this.delegate.statusReactive(BDDMockito.any()))
				.willReturn(Mono.just(AppStatus.of("asd").build()));

		this.traceAppDeployer.undeploy("asd");

		BDDAssertions.then(this.simpleTracer.getOnlySpan().tags).isNotEmpty();
		BDDMockito.then(this.delegate).should().undeploy(BDDMockito.any());
	}

	@Test
	void should_trace_status() {
		this.traceAppDeployer.status("asd");

		BDDAssertions.then(this.simpleTracer.getOnlySpan().tags).isNotEmpty();
		BDDMockito.then(this.delegate).should().status(BDDMockito.any());
	}

	@Test
	void should_trace_status_reactive() {
		BDDMockito.given(this.delegate.statusReactive(BDDMockito.any()))
				.willReturn(Mono.just(AppStatus.of("asd").build()));

		this.traceAppDeployer.statusReactive("asd").block();

		BDDAssertions.then(this.simpleTracer.getOnlySpan().tags).isNotEmpty();
		BDDMockito.then(this.delegate).should().statusReactive(BDDMockito.any());
	}

	@Test
	void should_trace_statuses_reactive() {
		BDDMockito.given(this.delegate.statusesReactive(BDDMockito.any()))
				.willReturn(Flux.just(AppStatus.of("asd").build()));

		this.traceAppDeployer.statusesReactive("asd").blockFirst();

		BDDAssertions.then(this.simpleTracer.getOnlySpan().tags).isNotEmpty();
		BDDMockito.then(this.delegate).should().statusesReactive(BDDMockito.any());
	}

	@Test
	void should_trace_log() {
		this.traceAppDeployer.getLog("id");

		BDDAssertions.then(this.simpleTracer.getOnlySpan().tags).isNotEmpty();
		BDDMockito.then(this.delegate).should().getLog(BDDMockito.any());
	}

	@Test
	void should_trace_scale() {
		this.traceAppDeployer.scale(new AppScaleRequest("asd", 2));

		BDDAssertions.then(this.simpleTracer.getOnlySpan().tags).isNotEmpty();
		BDDMockito.then(this.delegate).should().scale(BDDMockito.any());
	}

	private AppDeploymentRequest deploymentRequest() {
		return new AppDeploymentRequest(new AppDefinition("foo", new HashMap<>()), new PathResource("/"),
				deploymentProps(), commandLineArgs());
	}

	private List<String> commandLineArgs() {
		return Arrays.asList("foo=bar1", "baz=bar2");
	}

	private Map<String, String> deploymentProps() {
		Map<String, String> map = new HashMap<>();
		map.put("deployment1", "prop1");
		map.put("deployment2", "prop2");
		return map;
	}

	private BeanFactory beanFactory() {
		StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
		beanFactory.addBean("tracer", this.simpleTracer);
		beanFactory.addBean("currentTraceContext", new SimpleCurrentTraceContext());
		return beanFactory;
	}

	private Environment environment() {
		return new MockEnvironment();
	}

}
