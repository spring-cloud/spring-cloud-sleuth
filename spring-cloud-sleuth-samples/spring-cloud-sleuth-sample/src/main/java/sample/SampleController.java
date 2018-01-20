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

package sample;

import java.util.Random;
import java.util.concurrent.Callable;

import brave.Span;
import brave.Tracer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.context.ServletWebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

/**
 * @author Spencer Gibb
 */
@RestController
public class SampleController
		implements ApplicationListener<ServletWebServerInitializedEvent > {
	private static final Log log = LogFactory.getLog(SampleController.class);
	@Autowired
	private RestTemplate restTemplate;
	@Autowired
	private Tracer tracer;
	@Autowired
	private SampleBackground controller;

	private final Random random = new Random();
	private int port;

	@RequestMapping("/")
	public String hi() throws InterruptedException {
		log.info("hi!");
		Thread.sleep(this.random.nextInt(1000));

		String s = this.restTemplate
				.getForObject("http://localhost:" + this.port + "/hi2", String.class);
		return "hi/" + s;
	}

	@RequestMapping("/call")
	public Callable<String> call() {
		return new Callable<String>() {
			@Override
			public String call() throws Exception {
				log.info("call");
				int millis = SampleController.this.random.nextInt(1000);
				Thread.sleep(millis);
				SampleController.this.tracer.currentSpan().tag("callable-sleep-millis",
						String.valueOf(millis));
				Span span = SampleController.this.tracer.currentSpan();
				return "async hi: " + span;
			}
		};
	}

	@RequestMapping("/async")
	public String async() throws InterruptedException {
		log.info("async");
		this.controller.background();
		return "ho";
	}

	@RequestMapping("/hi2")
	public String hi2() throws InterruptedException {
		log.info("hi2!");
		int millis = this.random.nextInt(1000);
		Thread.sleep(millis);
		this.tracer.currentSpan().tag("random-sleep-millis", String.valueOf(millis));
		return "hi2";
	}

	@RequestMapping("/traced")
	public String traced() throws InterruptedException {
		log.info("traced");
		Span span = this.tracer.nextSpan().name("http:customTraceEndpoint");
		int millis = this.random.nextInt(1000);
		log.info(String.format("Sleeping for [%d] millis", millis));
		Thread.sleep(millis);
		this.tracer.currentSpan().tag("random-sleep-millis", String.valueOf(millis));

		String s = this.restTemplate
				.getForObject("http://localhost:" + this.port + "/call", String.class);
		span.finish();
		return "traced/" + s;
	}

	@RequestMapping("/start")
	public String start() throws InterruptedException {
		log.info("start");
		int millis = this.random.nextInt(1000);
		log.info(String.format("Sleeping for [%d] millis", millis));
		Thread.sleep(millis);
		this.tracer.currentSpan().tag("random-sleep-millis", String.valueOf(millis));

		String s = this.restTemplate
				.getForObject("http://localhost:" + this.port + "/call", String.class);
		return "start/" + s;
	}

	@Override
	public void onApplicationEvent(ServletWebServerInitializedEvent event) {
		this.port = event.getSource().getPort();
	}
}
