package org.springframework.cloud.sleuth.util;


import org.springframework.util.StringUtils;

/**
 * Helper class to convert Long to String or return null
 *
 * @author Marcin Grzejszczak
 */
public class LongUtils {

	public static String toString(Long value) {
		return value == null ? "" : Long.toString(value);
	}

	public static Long valueOf(String value) {
		return StringUtils.isEmpty(value) ? null : Long.valueOf(value);
	}
}
