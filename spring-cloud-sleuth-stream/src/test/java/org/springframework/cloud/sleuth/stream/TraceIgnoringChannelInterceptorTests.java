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

package org.springframework.cloud.sleuth.stream;

import javax.annotation.PostConstruct;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.cloud.sleuth.Sampler;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.cloud.sleuth.util.ExceptionUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.core.MessagingTemplate;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author Dave Syer
 */
@RunWith(SpringRunner.class)
@ContextConfiguration(classes = TraceIgnoringChannelInterceptorTests.App.class)
@DirtiesContext
public class TraceIgnoringChannelInterceptorTests {

	@Autowired Tracer tracer;
	@Autowired App app;
	@Autowired MessagingTemplate messagingTemplate;

	@Before
	public void init() {
		this.app.clear();
	}

	@After
	public void close() {
		then(ExceptionUtils.getLastException()).isNull();
		this.app.clear();
	}

	@Test
	public void shouldNotTraceTheTracer() {
		this.messagingTemplate.send(MessageBuilder.withPayload("hi").build());

		Spans spans = this.app.listener.poll();

		then(spans).isNull();
		then(this.tracer.getCurrentSpan()).isNull();
	}

	@Configuration
	@EnableAutoConfiguration
	static class App {

		private final BlockingQueue<Span> spans = new LinkedBlockingQueue<>();

		@Autowired StreamSpanReporter listener;

		@Bean MessagingTemplate messagingTemplate(SleuthSource sleuthSource) {
			return new MessagingTemplate(sleuthSource.output());
		}

		@Bean Sampler alwaysSampler() {
			return new AlwaysSampler();
		}


		@PostConstruct
		public void init() {
			this.listener.setQueue(this.spans);
		}

		public void clear() {
			this.spans.clear();
		}
	}
}
