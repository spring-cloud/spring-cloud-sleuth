/*
 * Copyright 2013-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.sleuth.util;

import org.apache.commons.logging.Log;

/**
 * Utility class for logging exceptions. Useful for test purposes - when a warning message
 * should be presented an exception can be thrown.
 * <p>
 * The purpose of this class is not to throw exceptions from the user's code when there
 * are some issues with tracing.
 *
 * @author Spencer Gibb
 * @since 1.0.0
 */
public final class ExceptionUtils {
	private static final Log log = org.apache.commons.logging.LogFactory
			.getLog(ExceptionUtils.class);
	private static boolean fail = false;
	private static Exception lastException = null;

	private ExceptionUtils() {
		throw new IllegalStateException("Utility class can't be instantiated");
	}

	public static void warn(String msg) {
		log.warn(msg);
		if (fail) {
			IllegalStateException exception = new IllegalStateException(msg);
			ExceptionUtils.lastException = exception;
			throw exception;
		}
	}

	public static Exception getLastException() {
		return ExceptionUtils.lastException;
	}

	public static void setFail(boolean fail) {
		ExceptionUtils.fail = fail;
		ExceptionUtils.lastException = null;
	}

	public static String getExceptionMessage(Throwable e) {
		return e.getMessage() != null ? e.getMessage() : e.toString();
	}
}
