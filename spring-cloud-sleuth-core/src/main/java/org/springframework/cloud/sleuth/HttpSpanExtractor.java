package org.springframework.cloud.sleuth;

/**
 * Contract for extracting tracing headers from a {@link SpanTextMap}
 * via HTTP headers
 *
 * @author Marcin Grzejszczak
 * @since 1.2.0
 */
public interface HttpSpanExtractor extends SpanExtractor<SpanTextMap> {
}
