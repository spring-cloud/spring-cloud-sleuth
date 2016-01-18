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

package sample;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.embedded.EmbeddedServletContainerInitializedEvent;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Trace;
import org.springframework.cloud.sleuth.TraceAccessor;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.context.ApplicationListener;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.Random;
import java.util.concurrent.Callable;

/**
 * @author Spencer Gibb
 */
@Slf4j
@RestController
public class SampleController implements
ApplicationListener<EmbeddedServletContainerInitializedEvent> {
	@Autowired
	private RestTemplate restTemplate;
	@Autowired
	private Tracer tracer;
	@Autowired
	private TraceAccessor accessor;
	@Autowired
	private SampleBackground controller;
	@Autowired
	private Random random;
	private int port;

	@SneakyThrows
	@RequestMapping("/")
	public String hi() {
		Thread.sleep(random.nextInt(1000));

		String s = this.restTemplate.getForObject("http://localhost:" + this.port
				+ "/hi2", String.class);
		return "hi/" + s;
	}

	@RequestMapping("/call")
	public Callable<String> call() {
		return new Callable<String>() {
			@Override
			public String call() throws Exception {
				int millis = random.nextInt(1000);
				Thread.sleep(millis);
				SampleController.this.tracer.addTag("callable-sleep-millis", String.valueOf(millis));
				Span currentSpan = SampleController.this.accessor.getCurrentSpan();
				return "async hi: " + currentSpan;
			}
		};
	}

	@RequestMapping("/async")
	public String async() {
		this.controller.background();
		return "ho";
	}

	@SneakyThrows
	@RequestMapping("/hi2")
	public String hi2() {
		int millis = random.nextInt(1000);
		Thread.sleep(millis);
		this.tracer.addTag("random-sleep-millis", String.valueOf(millis));
		return "hi2";
	}

	@SneakyThrows
	@RequestMapping("/traced")
	public String traced() {
		Trace trace = this.tracer.startTrace("customTraceEndpoint",
				new AlwaysSampler());
		int millis = random.nextInt(1000);
		log.info("Sleeping for {} millis", millis);
		Thread.sleep(millis);
		this.tracer.addTag("random-sleep-millis", String.valueOf(millis));

		String s = this.restTemplate.getForObject("http://localhost:" + this.port
				+ "/call", String.class);
		this.tracer.close(trace);
		return "traced/" + s;
	}

	@SneakyThrows
	@RequestMapping("/start")
	public String start() {
		int millis = random.nextInt(1000);
		log.info("Sleeping for {} millis", millis);
		Thread.sleep(millis);
		this.tracer.addTag("random-sleep-millis", String.valueOf(millis));

		String s = this.restTemplate.getForObject("http://localhost:" + this.port
				+ "/call", String.class);
		return "start/" + s;
	}

	@Override
	public void onApplicationEvent(EmbeddedServletContainerInitializedEvent event) {
		this.port = event.getEmbeddedServletContainer().getPort();
	}
}
