package org.springframework.cloud.sleuth;

import java.util.Collections;

import org.junit.Test;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author Marcin Grzejszczak
 * @author Rob Winch
 * @author Spencer Gibb
 */
public class SpanTest {

	@Test
	public void should_convert_long_to_hex_string() throws Exception {
		long someLong = 123123L;

		String hexString = Span.toHex(someLong);

		then(hexString).isEqualTo("1e0f3");
	}

	@Test
	public void should_convert_hex_string_to_long() throws Exception {
		String hexString = "1e0f3";

		long someLong = Span.fromHex(hexString);

		then(someLong).isEqualTo(123123L);
	}

	@Test(expected = IllegalArgumentException.class)
	public void should_throw_exception_when_null_string_is_to_be_converted_to_long() throws Exception {
		Span.fromHex(null);
	}

	@Test(expected = UnsupportedOperationException.class) public void getAnnotationsReadOnly() {
		Span span = new Span(1, 2, new SpanName("http", "name"), 1L, Collections.<Long>emptyList(), 2L, true,
				true, "process");

		span.tags().put("a", "b");
	}

	@Test(expected = UnsupportedOperationException.class) public void getTimelineAnnotationsReadOnly() {
		Span span = new Span(1, 2, new SpanName("http", "name"), 1L, Collections.<Long>emptyList(), 2L, true,
				true, "process");

		span.logs().add(new Log(1, "1"));
	}
}