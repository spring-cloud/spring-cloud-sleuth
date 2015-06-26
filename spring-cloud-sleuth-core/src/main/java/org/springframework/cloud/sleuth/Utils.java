package org.springframework.cloud.sleuth;

import lombok.extern.apachecommons.CommonsLog;

/**
 * @author Spencer Gibb
 */
@CommonsLog
public abstract class Utils {
	public static void error(String msg) {
		log.error(msg);
		throw new RuntimeException(msg);
	}
}
