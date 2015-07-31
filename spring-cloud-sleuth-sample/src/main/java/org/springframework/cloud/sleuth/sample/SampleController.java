package org.springframework.cloud.sleuth.sample;

import java.util.Random;
import java.util.concurrent.Callable;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.embedded.EmbeddedServletContainerInitializedEvent;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Trace;
import org.springframework.cloud.sleuth.TraceContextHolder;
import org.springframework.cloud.sleuth.TraceScope;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.context.ApplicationListener;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

/**
 * @author Spencer Gibb
 */
@Slf4j
@RestController
class SampleController implements
ApplicationListener<EmbeddedServletContainerInitializedEvent> {
	@Autowired
	private RestTemplate restTemplate;
	@Autowired
	private Trace trace;
	@Autowired
	private SampleBackground controller;
	private int port;

	@SneakyThrows
	@RequestMapping("/")
	public String hi() {
		final Random random = new Random();
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
				final Random random = new Random();
				int millis = random.nextInt(1000);
				Thread.sleep(millis);
				SampleController.this.trace.addKVAnnotation("callable-sleep-millis", String.valueOf(millis));
				Span currentSpan = TraceContextHolder.getCurrentSpan();
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
		final Random random = new Random();
		int millis = random.nextInt(1000);
		Thread.sleep(millis);
		this.trace.addKVAnnotation("random-sleep-millis", String.valueOf(millis));
		return "hi2";
	}

	@SneakyThrows
	@RequestMapping("/traced")
	public String traced() {
		TraceScope scope = this.trace.startSpan("customTraceEndpoint",
				new AlwaysSampler(), null);
		final Random random = new Random();
		int millis = random.nextInt(1000);
		log.info("Sleeping for {} millis", millis);
		Thread.sleep(millis);
		this.trace.addKVAnnotation("random-sleep-millis", String.valueOf(millis));

		String s = this.restTemplate.getForObject("http://localhost:" + this.port
				+ "/call", String.class);
		scope.close();
		return "traced/" + s;
	}

	@SneakyThrows
	@RequestMapping("/start")
	public String start() {
		final Random random = new Random();
		int millis = random.nextInt(1000);
		log.info("Sleeping for {} millis", millis);
		Thread.sleep(millis);
		this.trace.addKVAnnotation("random-sleep-millis", String.valueOf(millis));

		String s = this.restTemplate.getForObject("http://localhost:" + this.port
				+ "/call", String.class);
		return "start/" + s;
	}

	@Override
	public void onApplicationEvent(EmbeddedServletContainerInitializedEvent event) {
		this.port = event.getEmbeddedServletContainer().getPort();
	}
}
