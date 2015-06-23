package org.springframework.cloud.sleuth.trace;

import lombok.extern.apachecommons.CommonsLog;

/**
 * @author Spencer Gibb
 */
@CommonsLog
public abstract class Utils {
	public static void error(String msg) {
		error(msg);
		throw new RuntimeException(msg);
	}
}
