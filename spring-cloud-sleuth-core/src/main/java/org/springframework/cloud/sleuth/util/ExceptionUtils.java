package org.springframework.cloud.sleuth.util;

import lombok.extern.apachecommons.CommonsLog;

/**
 * @author Spencer Gibb
 */
@CommonsLog
public abstract class ExceptionUtils {
	public static void error(String msg) {
		log.error(msg);
		throw new RuntimeException(msg);
	}
}
