package org.springframework.cloud.sleuth.sampler;

import org.junit.Test;

import java.util.UUID;

import static org.assertj.core.api.BDDAssertions.then;
import static org.junit.Assert.fail;

public class DefaultStringToUuidConverterTest {

	DefaultStringToUuidConverter defaultStringToUuidConverter = new DefaultStringToUuidConverter();

	@Test
	public void should_successfully_convert_string_to_uuid_and_increment_success_counter() throws Exception {
		UUID expectedUuid = UUID.randomUUID();
		String uuidAsString = expectedUuid.toString();

		UUID convertedUuid = defaultStringToUuidConverter.convert(uuidAsString);

		then(convertedUuid).isEqualTo(expectedUuid);
		then(defaultStringToUuidConverter.successes.get()).isEqualTo(1L);
	}

	@Test
	public void should_fail_to_convert_string_to_uuid_and_increment_failures_counter() throws Exception {
		String invalidUuidString = "non UUID format string";

		try {
			defaultStringToUuidConverter.convert(invalidUuidString);
			fail();
		} catch (InvalidUuidStringFormatException e) {
			then(defaultStringToUuidConverter.failures.get()).isEqualTo(1L);
		}
	}
}