/*
 * Copyright 2018-2021 the original author or authors.
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

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.cloud.deployer.spi.app.AppDeployer;
import org.springframework.cloud.deployer.spi.app.AppScaleRequest;
import org.springframework.cloud.deployer.spi.app.AppStatus;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.spi.core.RuntimeEnvironmentInfo;
import org.springframework.cloud.sleuth.CurrentTraceContext;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.instrument.reactor.ReactorSleuth;

/**
 * Trace representation of an {@link AppDeployer}.
 *
 * @author Marcin Grzejszczak
 * @since 3.1.0
 */
public class TraceAppDeployer implements AppDeployer {

	private final AppDeployer delegate;

	private final BeanFactory beanFactory;

	private Tracer tracer;

	private CurrentTraceContext currentTraceContext;

	public TraceAppDeployer(AppDeployer delegate, BeanFactory beanFactory) {
		this.delegate = delegate;
		this.beanFactory = beanFactory;
	}

	@Override
	public String deploy(AppDeploymentRequest request) {
		Span span = tracer().nextSpan().name("deploy");
		// TODO: Is this secure to pass?
		// TODO: Does it make sense?
		span.tag("commandlineArguments", request.getCommandlineArguments().toString());
		span.tag("deploymentProperties", request.getDeploymentProperties().toString());
		try (Tracer.SpanInScope spanInScope = tracer().withSpan(span.start())) {
			String id = this.delegate.deploy(request);
			span.tag("id", id);
			return id;
		}
		finally {
			span.end();
		}
	}

	@Override
	public void undeploy(String id) {
		Span span = tracer().nextSpan().name("undeploy");
		span.tag("id", id);
		try (Tracer.SpanInScope spanInScope = tracer().withSpan(span.start())) {
			this.delegate.undeploy(id);
		}
		finally {
			span.end();
		}
	}

	@Override
	public AppStatus status(String id) {
		Span span = tracer().nextSpan().name("status");
		span.tag("id", id);
		try (Tracer.SpanInScope spanInScope = tracer().withSpan(span.start())) {
			return this.delegate.status(id);
		}
		finally {
			span.end();
		}
	}

	@Override
	public Mono<AppStatus> statusReactive(String id) {
		return ReactorSleuth.tracedMono(tracer(), currentTraceContext(), "status",
				() -> this.delegate.statusReactive(id), span -> span.tag("id", id));
	}

	@Override
	public Flux<AppStatus> statusesReactive(String... ids) {
		return ReactorSleuth.tracedFlux(tracer(), currentTraceContext(), "statuses",
				() -> this.delegate.statusesReactive(ids), span -> span.tag("ids", Arrays.toString(ids)));
	}

	@Override
	public RuntimeEnvironmentInfo environmentInfo() {
		return this.delegate.environmentInfo();
	}

	@Override
	public String getLog(String id) {
		Span span = tracer().nextSpan().name("getLog");
		span.tag("id", id);
		try (Tracer.SpanInScope spanInScope = tracer().withSpan(span.start())) {
			return this.delegate.getLog(id);
		}
		finally {
			span.end();
		}
	}

	@Override
	public void scale(AppScaleRequest appScaleRequest) {
		Span span = tracer().nextSpan().name("scale");
		span.tag("deploymentId", appScaleRequest.getDeploymentId());
		span.tag("count", String.valueOf(appScaleRequest.getCount()));
		// TODO: Is this secure to pass?
		// TODO: Does it make sense?
		span.tag("properties", appScaleRequest.getProperties().orElse(new HashMap<>()).toString());
		try (Tracer.SpanInScope spanInScope = tracer().withSpan(span.start())) {
			this.delegate.scale(appScaleRequest);
		}
		finally {
			span.end();
		}
	}

	private Tracer tracer() {
		if (this.tracer == null) {
			this.tracer = this.beanFactory.getBean(Tracer.class);
		}
		return this.tracer;
	}

	private CurrentTraceContext currentTraceContext() {
		if (this.currentTraceContext == null) {
			this.currentTraceContext = this.beanFactory.getBean(CurrentTraceContext.class);
		}
		return this.currentTraceContext;
	}

}
