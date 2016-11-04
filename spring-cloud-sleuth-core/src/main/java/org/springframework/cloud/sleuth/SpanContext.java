package org.springframework.cloud.sleuth;

import java.util.Map;

/**
 * Adopted from: https://github.com/opentracing/opentracing-java/blob/0.16.0/opentracing-api/src/main/java/io/opentracing/SpanContext.java
 *
 * SpanContext represents Span state that must propagate to descendant Spans and across process boundaries.
 *
 * SpanContext is logically divided into two pieces: (1) the user-level "Baggage" that propagates across Span
 * boundaries and (2) any Tracer-implementation-specific fields that are needed to identify or otherwise contextualize
 * the associated Span instance (e.g., a <trace_id, span_id, sampled> tuple).
 *
 * @see Span#setBaggageItem(String, String)
 * @see Span#getBaggageItem(String)
 *
 * @author Marcin Grzejszczak
 * @since 1.2.0
 */
public interface SpanContext {
	/**
	 * @return all zero or more baggage items propagating along with the associated Span
	 *
	 * @see Span#setBaggageItem(String, String)
	 * @see Span#getBaggageItem(String)
	 */
	Iterable<Map.Entry<String, String>> baggageItems();
}