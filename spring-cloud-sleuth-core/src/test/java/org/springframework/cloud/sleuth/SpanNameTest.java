package org.springframework.cloud.sleuth;

import org.junit.Test;

import static org.springframework.cloud.sleuth.assertions.SleuthAssertions.then;

/**
 * @author Marcin Grzejszczak
 */
public class SpanNameTest {

	@Test
	public void should_return_span_name_wihout_fragment_when_no_fragment_is_passed() {
		then(new SpanName("protocol", "address").toString())
				.isEqualTo("protocol:address");
	}

	@Test
	public void should_return_span_name_together_with_fragment_when_fragment_was_passed() {
		then(new SpanName("protocol", "address", "fragment").toString())
				.isEqualTo("protocol:address#fragment");
	}

	@Test
	public void should_return_empty_string_if_there_is_no_span_name() {
		then(SpanName.NO_NAME.toString()).isEmpty();
	}

	@Test
	public void should_build_span_from_valid_string_name() {
		then(SpanName.fromString("http:/a/b/c#async"))
				.hasProtocolEqualTo("http")
				.hasAddressEqualTo("/a/b/c")
				.hasFragmentEqualTo("async");
	}

	@Test
	public void should_build_span_from_a_string_with_missing_protocol() {
		then(SpanName.fromString("/a/b/c").toString())
				.isEqualTo("unknown:/a/b/c");
	}

	@Test
	public void should_build_span_from_a_string_name_with_many_separators() {
		then(SpanName.fromString("http:/a/b:/c#async:asd=123#4444"))
				.hasProtocolEqualTo("http")
				.hasAddressEqualTo("/a/b:/c")
				.hasFragmentEqualTo("async:asd=123#4444");
	}

}