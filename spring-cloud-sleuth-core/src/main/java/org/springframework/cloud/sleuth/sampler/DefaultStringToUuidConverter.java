package org.springframework.cloud.sleuth.sampler;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Default implementation that converts String into UUID
 * On parse exceptions an exception is thrown and logged.
 *
 * Successful and failed parse attempts are registered in counters.
 */
public class DefaultStringToUuidConverter implements StringToUuidConverter {

	protected final AtomicLong successes = new AtomicLong();
	protected final AtomicLong failures = new AtomicLong();

	@Override
	public UUID convert(String source) throws InvalidUuidStringFormatException {
		try {
			UUID uuid = UUID.fromString(source);
			successes.incrementAndGet();
			return uuid;
		} catch (IllegalArgumentException e) {
			failures.incrementAndGet();
			throw new InvalidUuidStringFormatException(source);
		}
	}
}
