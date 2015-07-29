package org.springframework.cloud.sleuth;

import lombok.Data;

/**
 * @author Spencer Gibb
 */
@Data
public class BasicSpanIdentifiers implements SpanIdentifiers {
	private final String traceId;
	private final String spanId;
	private String processId;
}
