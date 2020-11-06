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

package org.springframework.cloud.sleuth.baggage.multiple;

import java.util.Arrays;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.sleuth.api.BaggageInScope;
import org.springframework.cloud.sleuth.api.Span;
import org.springframework.cloud.sleuth.api.Tracer;
import org.springframework.http.HttpHeaders;
import org.springframework.integration.annotation.Aggregator;
import org.springframework.integration.annotation.Gateway;
import org.springframework.integration.annotation.IntegrationComponentScan;
import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.MessagingGateway;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.annotation.Splitter;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.cloud.sleuth.baggage.multiple.MultipleHopsIntegrationTests.COUNTRY_CODE;

@MessagingGateway(name = "greeter")
interface Sender {

	@Gateway(requestChannel = "greetings")
	void send(String message);

}

@RestController
@MessageEndpoint
@IntegrationComponentScan
public class DemoApplication {

	private static final Log log = LogFactory.getLog(DemoApplication.class);

	Span httpSpan;

	Span splitterSpan;

	Span aggregatorSpan;

	Span serviceActivatorSpan;

	@Autowired
	Sender sender;

	@Autowired
	Tracer tracer;

	String baggageValue;

	@RequestMapping("/greeting")
	public Greeting greeting(@RequestParam(defaultValue = "Hello World!") String message,
			@RequestHeader HttpHeaders headers) {
		this.sender.send(message);
		this.httpSpan = this.tracer.currentSpan();

		// tag what was propagated
		try (BaggageInScope baggageInScope = this.tracer.getBaggage(COUNTRY_CODE)) {
			if (baggageInScope != null) {
				String baggage = baggageInScope.get();
				if (baggage != null) {
					this.baggageValue = baggage;
					this.httpSpan.tag(COUNTRY_CODE, baggage);
				}
			}

			return new Greeting(message);
		}
	}

	@Splitter(inputChannel = "greetings", outputChannel = "words")
	public List<String> words(String greeting) {
		this.splitterSpan = this.tracer.currentSpan();
		return Arrays.asList(StringUtils.delimitedListToStringArray(greeting, " "));
	}

	@Aggregator(inputChannel = "words", outputChannel = "counts")
	public int count(List<String> greeting) {
		this.aggregatorSpan = this.tracer.currentSpan();
		return greeting.size();
	}

	@ServiceActivator(inputChannel = "counts")
	public void report(int count) {
		this.serviceActivatorSpan = this.tracer.currentSpan();
		log.info("Count: " + count);
	}

	public Span getHttpSpan() {
		return this.httpSpan;
	}

	public Span getSplitterSpan() {
		return this.splitterSpan;
	}

	public Span getAggregatorSpan() {
		return this.aggregatorSpan;
	}

	public Span getServiceActivatorSpan() {
		return this.serviceActivatorSpan;
	}

	public String getBaggageValue() {
		return this.baggageValue;
	}

	public List<Span> allSpans() {
		return Arrays.asList(this.httpSpan, this.splitterSpan, this.aggregatorSpan, this.serviceActivatorSpan);
	}

}

class Greeting {

	private String message;

	Greeting() {
	}

	Greeting(String message) {
		super();
		this.message = message;
	}

	public String getMessage() {
		return this.message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

}
