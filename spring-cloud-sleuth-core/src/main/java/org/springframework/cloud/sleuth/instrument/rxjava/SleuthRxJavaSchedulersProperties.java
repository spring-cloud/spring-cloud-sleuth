package org.springframework.cloud.sleuth.instrument.rxjava;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for RxJava tracing
 *
 * @author Arthur Gavlyukovskiy
 * @since 1.0.12
 */
@ConfigurationProperties("spring.sleuth.rxjava.schedulers")
public class SleuthRxJavaSchedulersProperties {

	/**
	 * Thread names for which spans will not be sampled.
	 */
	private String[] ignoredthreads = { "HystrixMetricPoller", "^RxComputation.*$" };
	private Hook hook = new Hook();

	public String[] getIgnoredthreads() {
		return this.ignoredthreads;
	}

	public void setIgnoredthreads(String[] ignoredthreads) {
		this.ignoredthreads = ignoredthreads;
	}

	public Hook getHook() {
		return this.hook;
	}

	public void setHook(Hook hook) {
		this.hook = hook;
	}

	private static class Hook {

		/**
		 * Enable support for RxJava via RxJavaSchedulersHook.
		 */
		private boolean enabled = true;

		public boolean isEnabled() {
			return this.enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}
	}
}
