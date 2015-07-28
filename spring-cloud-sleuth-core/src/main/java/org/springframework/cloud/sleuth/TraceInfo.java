package org.springframework.cloud.sleuth;

import lombok.Data;

/**
 * @author Spencer Gibb
 */
@Data
public class TraceInfo {
	private final String traceId;
	private final String spanId;
	private final Span.Type type;
}
