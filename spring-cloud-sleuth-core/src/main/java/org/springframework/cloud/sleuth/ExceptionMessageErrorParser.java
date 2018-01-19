package org.springframework.cloud.sleuth;

import brave.SpanCustomizer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * {@link ErrorParser} that sets the error tag for an exportable span.
 *
 * @author Marcin Grzejszczak
 * @since 1.2.1
 */
public class ExceptionMessageErrorParser implements ErrorParser {

	private static final Log log = LogFactory.getLog(ExceptionMessageErrorParser.class);

	@Override
	public void parseErrorTags(SpanCustomizer span, Throwable error) {
		if (span != null && error != null) {
			String errorMsg = getExceptionMessage(error);
			if (log.isDebugEnabled()) {
				log.debug("Adding an error tag [" + errorMsg + "] to span " + span);
			}
			span.tag("error", errorMsg);
		}
	}

	private String getExceptionMessage(Throwable e) {
		return e.getMessage() != null ? e.getMessage() : e.toString();
	}
}
