package org.springframework.cloud.sleuth;

/**
 * Contract for injecting tracing headers from a {@link SpanTextMap}
 * via HTTP headers
 *
 * @author Marcin Grzejszczak
 * @since 1.2.0
 */
public interface HttpSpanInjector extends SpanInjector<SpanTextMap> {
}
