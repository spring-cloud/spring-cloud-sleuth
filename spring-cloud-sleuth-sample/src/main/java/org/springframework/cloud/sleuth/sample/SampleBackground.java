package org.springframework.cloud.sleuth.sample;

import java.util.Random;

import lombok.SneakyThrows;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.sleuth.Trace;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * @author Spencer Gibb
 */
@Component
public class SampleBackground {

	@Autowired
	private Trace trace;

	@SneakyThrows
	@Async
	public void background() {
		final Random random = new Random();
		int millis = random.nextInt(1000);
		Thread.sleep(millis);
		this.trace.addKVAnnotation("background-sleep-millis", String.valueOf(millis));
	}

}
