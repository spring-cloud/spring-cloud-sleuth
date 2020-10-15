package org.springframework.cloud.sleuth.api;

import java.io.Closeable;

import org.springframework.lang.Nullable;

/*
 * Taken mostly from Brave.
 */
public interface Tracer extends BaggageManager {

	Span nextSpan();

	Span nextSpan(TraceContext parent);

	Tracer.SpanInScope withSpan(@Nullable Span span);

	ScopedSpan startScopedSpan(String name);

	ScopedSpan startScopedSpan(String name, @Nullable Span parent);

	Span.Builder spanBuilder();

	@Nullable
	SpanCustomizer currentSpanCustomizer();

	@Nullable
	Span currentSpan();

	interface SpanInScope extends Closeable {

		@Override
		void close();

	}

}
