package org.springframework.cloud.sleuth;

import org.springframework.cloud.sleuth.util.ExceptionUtils;

/**
 * {@link ErrorParser} that returns an exception message of a given error.
 *
 * @author Marcin Grzejszczak
 * @since 1.2.1
 */
public class ExceptionMessageErrorParser implements ErrorParser {
	@Override
	public String parseError(Throwable error) {
		return ExceptionUtils.getExceptionMessage(error);
	}
}
