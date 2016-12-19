package org.springframework.cloud.sleuth.util;

import org.springframework.http.HttpStatus;

/**
 * Small utility class to check if a status code is valid
 *
 * @author Florian Lautenschlager
 */
public final class HttpStatusUtil {

	private static final HttpStatus[] REGULAR_STATUS_CODES = HttpStatus.values();
	private static int statusCodeLength = REGULAR_STATUS_CODES.length;

	private HttpStatusUtil() {
		//avoid instances
	}

	/**
	 * Uses {@link HttpStatus} to validate if a status code is regular or not.
	 *
	 * @param value of the http status (numeric)
	 * @return true if the http status is a regular, otherwise false
	 */
	public static boolean isRegularStatus(int value) {
		for (int i = 0; i < statusCodeLength; ++i) {
			HttpStatus httpStatus = REGULAR_STATUS_CODES[i];
			if (httpStatus.value() == value) {
				return true;
			}
		}
		return false;
	}
}
