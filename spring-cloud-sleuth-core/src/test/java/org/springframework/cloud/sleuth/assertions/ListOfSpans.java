package org.springframework.cloud.sleuth.assertions;

import java.util.List;

import org.springframework.cloud.sleuth.Span;

/**
 * @author Marcin Grzejszczak
 */
public class ListOfSpans {

	public final List<Span> spans;

	public ListOfSpans(List<Span> spans) {
		this.spans = spans;
	}
}
