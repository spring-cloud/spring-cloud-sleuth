package org.springframework.cloud.sleuth;

import org.assertj.core.api.BDDAssertions;
import org.junit.Test;

/**
 * @author Marcin Grzejszczak
 */
public class InternalApiTests {

	@Test
	public void should_rename_a_span() {
		Span span = Span.builder().name("foo").build();

		InternalApi.renameSpan(span, "bar");

		BDDAssertions.then(span.getName()).isEqualTo("bar");
	}
}