package org.springframework.cloud.sleuth;

import org.junit.Test;

import static org.springframework.cloud.sleuth.assertions.SleuthAssertions.then;

/**
 * @author Marcin Grzejszczak
 */
public class ExceptionMessageErrorParserTests {

	@Test
	public void should_append_tag_for_exportable_span() throws Exception {
		Throwable e = new RuntimeException("foo");
		Span span = new Span.SpanBuilder().exportable(true).build();

		new ExceptionMessageErrorParser().parseErrorTags(span, e);

		then(span).hasATag("error", "foo");
	}

	@Test
	public void should_not_throw_an_exception_when_span_is_null() throws Exception {
		new ExceptionMessageErrorParser().parseErrorTags(null, null);
	}

	@Test
	public void should_not_append_tag_for_non_exportable_span() throws Exception {
		Throwable e = new RuntimeException("foo");
		Span span = new Span.SpanBuilder().exportable(false).build();

		new ExceptionMessageErrorParser().parseErrorTags(span, e);

		then(span.tags()).isEmpty();
	}

}