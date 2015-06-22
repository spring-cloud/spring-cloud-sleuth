package org.springframework.cloud.sleuth.correlation;

import java.util.concurrent.Callable;

/**
 * @author Spencer Gibb
 */
public interface CorrelationProvider {

	String getCorrelationId(String location, Callable<String> correlationIdGetter);

	void correlationIdSet(String correlationId);

	void startTrace(Object context);

	void endTrace(Object context);

}
