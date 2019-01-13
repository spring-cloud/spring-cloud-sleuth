package org.springframework.cloud.sleuth.instrument.async;

/**
 * @author Marcin Grzejszczak
 * @since
 */
public class ContextRefreshedListenerAccessor {

	public static void clear() {
		ContextRefreshedListener.INSTANCE = new ContextRefreshedListener(false);
	}

	public static void refreshed() {
		ContextRefreshedListener.INSTANCE = new ContextRefreshedListener(true);
	}

}
