package org.springframework.cloud.sleuth;

import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.sleuth.util.ExceptionUtils;

import java.lang.invoke.MethodHandles;

/**
 * {@link ErrorParser} that sets the error tag for an exportable span.
 *
 * @author Marcin Grzejszczak
 * @since 1.2.1
 */
public class ExceptionMessageErrorParser implements ErrorParser {

	private static final org.apache.commons.logging.Log log = LogFactory.getLog(MethodHandles.lookup().lookupClass());

	@Override
	public void parseErrorTags(Span span, Throwable error) {
		if (span != null && span.isExportable()) {
			String errorMsg = ExceptionUtils.getExceptionMessage(error);
			if (log.isDebugEnabled()) {
				log.debug("Adding an error tag [" + errorMsg + "] to span " + span);
			}
			span.tag(Span.SPAN_ERROR_TAG_NAME, errorMsg);
		}
	}
}
