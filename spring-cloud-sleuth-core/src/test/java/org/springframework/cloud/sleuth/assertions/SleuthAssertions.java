package org.springframework.cloud.sleuth.assertions;

import org.assertj.core.api.BDDAssertions;
import org.springframework.cloud.sleuth.Span;

public class SleuthAssertions extends BDDAssertions {

	public static SpanAssert then(Span actual) {
		return new SpanAssert(actual);
	}

}
