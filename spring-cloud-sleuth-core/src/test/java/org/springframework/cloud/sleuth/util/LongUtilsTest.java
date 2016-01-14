package org.springframework.cloud.sleuth.util;

import org.junit.Test;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author Marcin Grzejszczak
 */
public class LongUtilsTest {

	@Test
	public void should_convert_to_empty_string_if_long_is_null() {
		then(LongUtils.toString(null)).isNullOrEmpty();
	}

	@Test
	public void should_convert_to_to_string_if_long_has_value() {
		Long longValue = 100L;

		String result = LongUtils.toString(longValue);

		then(result).isEqualTo("100");
	}

	@Test
	public void should_convert_to_null_if_string_is_null_or_empty() {
		then(LongUtils.valueOf(null)).isNull();
		then(LongUtils.valueOf("")).isNull();
	}

	@Test
	public void should_convert_to_to_long_if_string_has_value() {
		String longValue = "100";

		Long result = LongUtils.valueOf(longValue);

		then(result).isEqualTo(100L);
	}

}