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

package org.springframework.cloud.sleuth.otel.instrument.web.baggage;

import java.lang.invoke.MethodHandles;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.context.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import org.springframework.cloud.sleuth.api.BaggageInScope;
import org.springframework.cloud.sleuth.api.Span;
import org.springframework.cloud.sleuth.api.Tracer;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

@RestController
public class Service1Controller {

	private final Service2Client service2Client;

	public Service1Controller(Service2Client service2Client) {
		this.service2Client = service2Client;
	}

	@GetMapping("/start")
	public Mono<String> start() {
		return this.service2Client.start();
	}

	@GetMapping("/startWithOtel")
	public Mono<String> startOtel() {
		return this.service2Client.startWithOtelOnly();
	}

}

@Component
class Service2Client {

	private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final WebClient webClient;

	private final String serviceAddress;

	private final Tracer tracer;

	String superSecretBaggage;

	String baggageKey;

	Service2Client(WebClient webClient, String serviceAddress, Tracer tracer) {
		this.webClient = webClient;
		this.serviceAddress = serviceAddress;
		this.tracer = tracer;
	}

	public Mono<String> start() {
		log.info("Hello from service1. Setting baggage foo=>bar");
		Span span = tracer.currentSpan();
		BaggageInScope secretBaggageField = this.tracer.getBaggage("baggage");
		String secretBaggage = secretBaggageField != null ? secretBaggageField.get() : null;
		log.info("Super secret baggage item for key [baggage] is [{}]", secretBaggage);
		this.superSecretBaggage = secretBaggage;
		if (StringUtils.hasText(secretBaggage)) {
			span.event("secret_baggage_received");
			span.tag("baggage", secretBaggage);
		}
		String baggageKey = "key";
		String baggageValue = "foo";
		BaggageInScope baggageField = this.tracer.createBaggage(baggageKey);
		baggageField.set(span.context(), baggageValue);
		span.event("baggage_set");
		span.tag(baggageKey, baggageValue);
		log.info("Hello from service1. Calling service2");
		return webClient.get().uri(serviceAddress + "/foo").exchange().doOnSuccess(clientResponse -> {
			log.info("Got response from service2 [{}]", clientResponse);
			try (BaggageInScope bs = this.tracer.getBaggage("key")) {
				this.baggageKey = bs.get();
				log.info("Service1: Baggage for [key] is [" + (bs == null ? null : bs.get()) + "]");
			}
		}).flatMap(clientResponse -> clientResponse.bodyToMono(String.class)).doOnTerminate(() -> {
			if (secretBaggageField != null) {
				secretBaggageField.close();
			}
			baggageField.close();
		});
	}

	public Mono<String> startWithOtelOnly() {
		log.info("Hello from service1. Setting baggage foo=>bar");
		io.opentelemetry.api.trace.Span span = io.opentelemetry.api.trace.Span.current();
		String secretBaggage = Baggage.current().getEntryValue("baggage");
		log.info("Super secret baggage item for key [baggage] is [{}]", secretBaggage);
		this.superSecretBaggage = secretBaggage;
		if (StringUtils.hasText(secretBaggage)) {
			span.addEvent("secret_baggage_received");
			span.setAttribute("baggage", secretBaggage);
		}
		String baggageKey = "key";
		String baggageValue = "foo";
		Baggage baggage = Baggage.builder().put(baggageKey, baggageValue).build();
		Scope baggageField = baggage.makeCurrent();
		span.addEvent("baggage_set");
		span.setAttribute(baggageKey, baggageValue);
		log.info("Hello from service1. Calling service2");
		return webClient.get().uri(serviceAddress + "/foo").exchange().doOnSuccess(clientResponse -> {
			log.info("Got response from service2 [{}]", clientResponse);
			// This will not work
			// String key = Baggage.current().getEntryValue("key");
			try (Scope scope = baggage.makeCurrent()) {
				String key = Baggage.current().getEntryValue("key");
				this.baggageKey = key;
				log.info("Service1: Baggage for [key] is [" + key + "]");
			}
		}).flatMap(clientResponse -> clientResponse.bodyToMono(String.class)).doOnTerminate(baggageField::close);
	}

	String getSuperSecretBaggage() {
		return this.superSecretBaggage;
	}

	String getBaggageKey() {
		return this.baggageKey;
	}

	void reset() {
		this.superSecretBaggage = null;
		this.baggageKey = null;
	}

}
