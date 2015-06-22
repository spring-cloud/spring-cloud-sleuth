package org.springframework.cloud.sleuth.correlation.slf4j;

import static org.springframework.cloud.sleuth.correlation.CorrelationIdHolder.CORRELATION_ID_HEADER;
import static org.springframework.util.StringUtils.hasText;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.slf4j.MDC;
import org.springframework.cloud.sleuth.correlation.CorrelationProvider;

import java.util.concurrent.Callable;

/**
 * @author Spencer Gibb
 */
@Slf4j
public class Slf4jCorrelationProvider implements CorrelationProvider {
	@Override
	public String getCorrelationId(String location, Callable<String> correlationIdGetter) {
		String correlationId = tryToGetCorrelationId(correlationIdGetter);
		if (hasText(correlationId)) {
			MDC.put(CORRELATION_ID_HEADER, correlationId);
			log.debug("Found correlationId in " + location + ": " + correlationId);
		}
		return correlationId;
	}

	private String tryToGetCorrelationId(Callable<String> correlationIdGetter) {
		try {
			return correlationIdGetter.call();
		} catch (Exception e) {
			log.error("Exception occurred while trying to retrieve request header", e);
			return StringUtils.EMPTY;
		}
	}

	@Override
	public void correlationIdSet(String correlationId) {
		MDC.put(CORRELATION_ID_HEADER, correlationId);
	}

}
