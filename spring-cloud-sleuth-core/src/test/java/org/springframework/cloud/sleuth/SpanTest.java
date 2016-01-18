package org.springframework.cloud.sleuth;

import org.junit.Test;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author Marcin Grzejszczak
 */
public class SpanTest {

	@Test
	public void should_convert_long_to_hex_string() throws Exception {
		long someLong = 123123L;

		String hexString = Span.Converter.toHexString(someLong);

		then(hexString).isEqualTo("1e0f3");
	}

	@Test
	public void should_convert_hex_string_to_long() throws Exception {
		String hexString = "1e0f3";

		long someLong = Span.Converter.fromHexString(hexString);

		then(someLong).isEqualTo(123123L);
	}

	@Test(expected = IllegalArgumentException.class)
	public void should_throw_exception_when_null_string_is_to_be_converted_to_long() throws Exception {
		Span.Converter.fromHexString(null);
	}
}