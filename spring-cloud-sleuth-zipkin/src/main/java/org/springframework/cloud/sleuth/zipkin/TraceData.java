package org.springframework.cloud.sleuth.zipkin;

import lombok.Data;

/**
* @author Spencer Gibb
*/
@Data
public class TraceData {
    private Long traceId;
    private Long spanId;
    private Long parentSpanId;
    private Boolean shouldBeSampled;
    private String spanName;
}
