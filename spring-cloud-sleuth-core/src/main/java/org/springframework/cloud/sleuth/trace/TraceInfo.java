package org.springframework.cloud.sleuth.trace;

import lombok.Data;

/**
 * @author Spencer Gibb
 */
@Data
public class TraceInfo {
	private final long traceId;
	private final long spanId;
}
