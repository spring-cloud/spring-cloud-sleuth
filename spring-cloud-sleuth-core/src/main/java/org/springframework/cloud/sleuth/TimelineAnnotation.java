package org.springframework.cloud.sleuth;

import lombok.Data;

/**
 * @author Spencer Gibb
 */
@Data
public class TimelineAnnotation {
	private final long time;
	private final String msg;
}
