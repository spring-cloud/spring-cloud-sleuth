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

import java.time.Duration;
import java.util.Arrays;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.cloud.deployer.spi.app.AppDeployer;
import org.springframework.cloud.deployer.spi.app.AppScaleRequest;
import org.springframework.cloud.deployer.spi.app.AppStatus;
import org.springframework.cloud.deployer.spi.app.DeploymentState;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.spi.core.RuntimeEnvironmentInfo;
import org.springframework.cloud.sleuth.CurrentTraceContext;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.docs.AssertingSpan;
import org.springframework.cloud.sleuth.docs.AssertingSpanBuilder;
import org.springframework.cloud.sleuth.docs.DocumentedSpan;
import org.springframework.cloud.sleuth.instrument.reactor.ReactorSleuth;
import org.springframework.core.env.Environment;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

/**
 * Trace representation of an {@link AppDeployer}.
 *
 * @author Marcin Grzejszczak
 * @since 3.1.0
 */
public class TraceAppDeployer implements AppDeployer {

	private static final Log log = LogFactory.getLog(TraceAppDeployer.class);

	private final AppDeployer delegate;

	private final BeanFactory beanFactory;

	private final Environment environment;

	private Tracer tracer;

	private CurrentTraceContext currentTraceContext;

	private Long pollDelay;

	public TraceAppDeployer(AppDeployer delegate, BeanFactory beanFactory, Environment environment) {
		this.delegate = delegate;
		this.beanFactory = beanFactory;
		this.environment = environment;
	}

	@Override
	public String deploy(AppDeploymentRequest request) {
		AssertingSpanBuilder spanBuilder = clientSpan(SleuthDeployerSpan.DEPLOYER_DEPLOY_SPAN, request);
		AssertingSpan span = spanBuilder.start();
		try (Tracer.SpanInScope spanInScope = tracer().withSpan(span)) {
			span.event(SleuthDeployerSpan.Events.DEPLOYER_START);
			String id = this.delegate.deploy(request);
			span.tag(SleuthDeployerSpan.Tags.APP_ID, id);
			registerListener(span, id);
			return id;
		}
	}

	private AssertingSpanBuilder clientSpan(DocumentedSpan documentedSpan) {
		return clientSpan(documentedSpan, null, null);
	}

	private AssertingSpanBuilder clientSpan(DocumentedSpan documentedSpan, @Nullable AppDeploymentRequest request) {
		return clientSpan(documentedSpan, null, request);
	}

	private AssertingSpanBuilder clientSpanKind(String name, AssertingSpanBuilder spanBuilder) {
		return spanBuilder.kind(Span.Kind.CLIENT).name(name).remoteServiceName(remoteServiceName());
	}

	private AssertingSpanBuilder clientSpan(DocumentedSpan documentedSpan, Span parentSpan) {
		return clientSpan(documentedSpan, parentSpan, null);
	}

	private AssertingSpanBuilder clientSpan(DocumentedSpan documentedSpan, @Nullable Span parentSpan,
			@Nullable AppDeploymentRequest request) {
		String name = documentedSpan.getName();
		AssertingSpanBuilder spanBuilder = AssertingSpanBuilder.of(documentedSpan, tracer().spanBuilder());
		Span currentSpan = parentSpan != null ? parentSpan : tracer().currentSpan();
		if (currentSpan != null) {
			spanBuilder.setParent(currentSpan.context());
		}
		Map<String, String> platformSpecificInfo = environmentInfo().getPlatformSpecificInfo();
		if (request != null) {
			String platformName = request.getDeploymentProperties().get("spring.cloud.deployer.platformName");
			if (StringUtils.hasText(platformName)) {
				spanBuilder.tag(SleuthDeployerSpan.Tags.PLATFORM_NAME, platformName);
			}
			String appName = request.getDeploymentProperties().get("spring.cloud.deployer.appName");
			if (StringUtils.hasText(appName)) {
				spanBuilder.tag(SleuthDeployerSpan.Tags.APP_NAME, appName);
			}
			String group = request.getDeploymentProperties().get("spring.cloud.deployer.group");
			if (StringUtils.hasText(group)) {
				spanBuilder.tag(SleuthDeployerSpan.Tags.APP_GROUP, group);
			}
		}
		addCfTags(spanBuilder, platformSpecificInfo);
		addK8sTags(spanBuilder, platformSpecificInfo);
		return clientSpanKind(name, spanBuilder);
	}

	private void addCfTags(AssertingSpanBuilder spanBuilder, Map<String, String> platformSpecificInfo) {
		if (platformSpecificInfo.containsKey("API Endpoint")) {
			spanBuilder.tag(SleuthDeployerSpan.Tags.CF_URL, platformSpecificInfo.get("API Endpoint"));
		}
		if (platformSpecificInfo.containsKey("Organization")) {
			spanBuilder.tag(SleuthDeployerSpan.Tags.CF_ORG, platformSpecificInfo.get("Organization"));
		}
		if (platformSpecificInfo.containsKey("Space")) {
			spanBuilder.tag(SleuthDeployerSpan.Tags.CF_SPACE, platformSpecificInfo.get("Space"));
		}
	}

	private void addK8sTags(AssertingSpanBuilder spanBuilder, Map<String, String> platformSpecificInfo) {
		if (platformSpecificInfo.containsKey("master-url")) {
			spanBuilder.tag(SleuthDeployerSpan.Tags.K8S_URL, platformSpecificInfo.get("master-url"));
		}
		if (platformSpecificInfo.containsKey("namespace")) {
			spanBuilder.tag(SleuthDeployerSpan.Tags.K8S_NAMESPACE, platformSpecificInfo.get("namespace"));
		}
	}

	private String remoteServiceName() {
		return environmentInfo().getPlatformType();
	}

	private void registerListener(Span span, String id) {
		PreviousAndCurrentStatus previousAndCurrentStatus = new PreviousAndCurrentStatus(span);
		// @formatter:off
			this.delegate.statusReactive(id)
					.map(previousAndCurrentStatus::updateCurrent)
					.repeatWhen(repeat -> repeat.flatMap(i -> Mono.delay(Duration.ofMillis(pollDelay()))))
					.takeUntil(PreviousAndCurrentStatus::isFinished)
					.last()
					.doOnNext(PreviousAndCurrentStatus::annotateSpan)
					.doOnError(span::error)
					// we will close the span in the reactive part
					.doFinally(signalType -> span.end()).subscribe();
			// @formatter:on
	}

	@Override
	public void undeploy(String id) {
		AssertingSpanBuilder spanBuilder = clientSpan(SleuthDeployerSpan.DEPLOYER_UNDEPLOY_SPAN);
		AssertingSpan span = spanBuilder.start();
		span.tag(SleuthDeployerSpan.Tags.APP_ID, id);
		try (Tracer.SpanInScope spanInScope = tracer().withSpan(span)) {
			span.event(SleuthDeployerSpan.Events.DEPLOYER_START);
			this.delegate.undeploy(id);
			registerListener(span, id);
		}
		finally {
			span.end();
		}
	}

	@Override
	public AppStatus status(String id) {
		AssertingSpanBuilder spanBuilder = clientSpan(SleuthDeployerSpan.DEPLOYER_STATUS_SPAN);
		AssertingSpan span = spanBuilder.start();
		span.tag(SleuthDeployerSpan.Tags.APP_ID, id);
		try (Tracer.SpanInScope spanInScope = tracer().withSpan(span.start())) {
			return this.delegate.status(id);
		}
		finally {
			span.end();
		}
	}

	@Override
	public Mono<AppStatus> statusReactive(String id) {
		return ReactorSleuth.tracedMono(tracer(), currentTraceContext(),
				SleuthDeployerSpan.DEPLOYER_STATUS_SPAN.getName(), () -> this.delegate.statusReactive(id),
				(o, span) -> span.tag(SleuthDeployerSpan.Tags.APP_ID.getKey(), id),
				span -> clientSpan(SleuthDeployerSpan.DEPLOYER_STATUS_SPAN, span).start());
	}

	@Override
	public Flux<AppStatus> statusesReactive(String... ids) {
		return ReactorSleuth.tracedFlux(tracer(), currentTraceContext(),
				SleuthDeployerSpan.DEPLOYER_STATUSES_SPAN.getName(), () -> this.delegate.statusesReactive(ids),
				(o, span) -> span.tag(SleuthDeployerSpan.Tags.APP_ID.getKey(), Arrays.toString(ids)),
				span -> clientSpan(SleuthDeployerSpan.DEPLOYER_STATUSES_SPAN, span).start());
	}

	@Override
	public RuntimeEnvironmentInfo environmentInfo() {
		return this.delegate.environmentInfo();
	}

	@Override
	public String getLog(String id) {
		AssertingSpanBuilder spanBuilder = clientSpan(SleuthDeployerSpan.DEPLOYER_GET_LOG_SPAN);
		AssertingSpan span = spanBuilder.start();
		span.tag(SleuthDeployerSpan.Tags.APP_ID, id);
		try (Tracer.SpanInScope spanInScope = tracer().withSpan(span)) {
			return this.delegate.getLog(id);
		}
		finally {
			span.end();
		}
	}

	@Override
	public void scale(AppScaleRequest appScaleRequest) {
		AssertingSpanBuilder spanBuilder = clientSpan(SleuthDeployerSpan.DEPLOYER_SCALE_SPAN);
		AssertingSpan span = spanBuilder.start();
		span.tag(SleuthDeployerSpan.ScaleTags.DEPLOYER_SCALE_DEPLOYMENT_ID, appScaleRequest.getDeploymentId());
		span.tag(SleuthDeployerSpan.ScaleTags.DEPLOYER_SCALE_COUNT, String.valueOf(appScaleRequest.getCount()));
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

	private long pollDelay() {
		if (this.pollDelay == null) {
			this.pollDelay = this.environment.getProperty("spring.sleuth.deployer.status-poll-delay", Long.class, 500L);
		}
		return this.pollDelay;
	}

	private static final class PreviousAndCurrentStatus {

		private final Span span;

		private AppStatus current;

		private AppStatus previous;

		private PreviousAndCurrentStatus(Span span) {
			this.span = span;
			if (log.isDebugEnabled()) {
				log.debug("Current span is [" + span + "]");
			}
		}

		private PreviousAndCurrentStatus updateCurrent(AppStatus current) {
			if (log.isTraceEnabled()) {
				log.trace("State before change: current [" + this.current + "], previous [" + this.previous + "]");
			}
			this.previous = this.current;
			this.current = current;
			if (log.isTraceEnabled()) {
				log.trace("State after change: current [" + this.current + "], previous [" + this.previous + "]");
			}
			if (statusChanged()) {
				annotateSpan();
			}
			else if (log.isTraceEnabled()) {
				log.trace("State has not changed, will not annotate the span");
			}
			return this;
		}

		private void annotateSpan() {
			String name = this.current.getState().name();
			if (log.isDebugEnabled()) {
				log.debug("Will annotate its state with [" + name + "]");
			}
			this.span.event(String.format(SleuthDeployerSpan.Events.DEPLOYER_STATUS_CHANGE.getValue(), name));
		}

		private boolean statusChanged() {
			if (this.previous == null && this.current != null) {
				if (log.isDebugEnabled()) {
					log.debug("Previous is null, current is not null");
				}
				return true;
			}
			else if (this.current == null) {
				throw new IllegalStateException("Current state can't be null");
			}
			DeploymentState currentState = this.current.getState();
			DeploymentState previousState = this.previous.getState();
			return currentState != previousState;
		}

		private boolean isFinished() {
			boolean finished = this.current.getState() == DeploymentState.deployed
					|| this.current.getState() == DeploymentState.undeployed
					|| this.current.getState() == DeploymentState.failed
					|| this.current.getState() == DeploymentState.error
					|| this.current.getState() == DeploymentState.unknown;
			if (log.isTraceEnabled()) {
				log.trace("Status is finished [" + finished + "]");
			}
			return finished;
		}

	}

}
