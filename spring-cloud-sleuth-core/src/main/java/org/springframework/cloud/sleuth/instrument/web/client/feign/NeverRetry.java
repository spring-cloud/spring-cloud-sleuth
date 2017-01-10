package org.springframework.cloud.sleuth.instrument.web.client.feign;

import feign.RetryableException;
import feign.Retryer;

/**
 * This is essentially the same implementation of a Retryer that is in newer versions of
 * Feign. For the 1.0.x stream we add it here.
 * @author Ryan Baxter
 */
public class NeverRetry implements Retryer {
	@Override
	public void continueOrPropagate(RetryableException e) {
		throw e;
	}

	@Override
	public Retryer clone() {
		return this;
	}

	public static final NeverRetry INSTANCE = new NeverRetry();
}
