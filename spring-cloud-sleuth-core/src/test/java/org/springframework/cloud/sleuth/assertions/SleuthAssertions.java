package org.springframework.cloud.sleuth.assertions;

import org.assertj.core.api.BDDAssertions;
import org.springframework.cloud.sleuth.Span;

public class SleuthAssertions extends BDDAssertions {

	public static SpanAssert then(Span actual) {
		return assertThat(actual);
	}

	public static SpanAssert assertThat(Span actual) {
		return new SpanAssert(actual);
	}


	public static ListOfSpansAssert then(ListOfSpans actual) {
		return assertThat(actual);
	}

	public static ListOfSpansAssert assertThat(ListOfSpans actual) {
		return new ListOfSpansAssert(actual);
	}

}
