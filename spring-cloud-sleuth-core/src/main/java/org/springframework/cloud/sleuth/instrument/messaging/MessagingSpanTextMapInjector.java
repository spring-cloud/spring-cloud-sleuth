package org.springframework.cloud.sleuth.instrument.messaging;

import org.springframework.cloud.sleuth.SpanInjector;
import org.springframework.cloud.sleuth.SpanTextMap;

/**
 * Contract for injecting tracing headers from a {@link SpanTextMap}
 * via message headers
 *
 * @author Marcin Grzejszczak
 * @since 1.2.0
 */
public interface MessagingSpanTextMapInjector extends SpanInjector<SpanTextMap> {
}
