package org.springframework.cloud.sleuth.api;

import java.io.Closeable;

import org.springframework.lang.Nullable;

public interface Tracer {

	Span newTrace();

	Span joinSpan(TraceContext context);

	Span newChild(TraceContext parent);

	Span nextSpan(TraceContext extracted);

	Span nextSpan(SamplingFlags extracted);

	Span toSpan(TraceContext context);

	Tracer.SpanInScope withSpanInScope(@Nullable Span span);

	SpanCustomizer currentSpanCustomizer();

	@Nullable
	Span currentSpan();

	Span nextSpan();

	ScopedSpan startScopedSpan(String name);

	<T> ScopedSpan startScopedSpan(String name, SamplerFunction<T> samplerFunction, T arg);

	<T> Span nextSpan(SamplerFunction<T> samplerFunction, T arg);

	<T> Span nextSpanWithParent(SamplerFunction<T> samplerFunction, T arg, @Nullable TraceContext parent);

	// this api is needed to make tools such as executors which need to carry the
	// invocation context
	ScopedSpan startScopedSpanWithParent(String name, @Nullable Span parent);

	interface SpanInScope extends Closeable {

		@Override
		void close();

	}

}
