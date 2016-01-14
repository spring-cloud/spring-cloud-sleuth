package org.springframework.cloud.sleuth.util;

import java.util.Random;

/**
 * @author Marcin Grzejszczak
 */
public class RandomLongSpanIdGenerator implements SpanIdGenerator {

	private Random random = new Random();

	@Override
	public long generateId() {
		return random.nextLong();
	}

	public void setRandom(Random random) {
		this.random = random != null ? random : new Random();
	}
}
