package org.springframework.cloud.sleuth.instrument.web;

import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.junit.Test;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanTextMap;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author Marcin Grzejszczak
 */
public class ZipkinHttpSpanInjectorTests {

	ZipkinHttpSpanInjector injector = new ZipkinHttpSpanInjector();

	@SuppressWarnings("unchecked")
	@Test
	public void should_not_override_already_existing_headers() throws Exception {
		Span span = Span.builder()
				.spanId(1L)
				.traceId(2L)
				.parent(3L)
				.baggage("foo", "bar")
				.name("span")
				.build();
		Map<String, String> holder = new HashMap<>();
		final SpanTextMap map = textMap(holder);
		holder.put(Span.SPAN_ID_NAME, Span.idToHex(10L));
		holder.put(Span.TRACE_ID_NAME, Span.idToHex(20L));
		holder.put(Span.PARENT_ID_NAME, Span.idToHex(30L));
		holder.put(Span.SPAN_NAME_NAME, "anotherSpan");

		injector.inject(span, map);

		then(map)
				.contains(new AbstractMap.SimpleEntry<String, String>(Span.SPAN_ID_NAME, Span.idToHex(10L)))
				.contains(new AbstractMap.SimpleEntry<String, String>(Span.TRACE_ID_NAME, Span.idToHex(20L)))
				.contains(new AbstractMap.SimpleEntry<String, String>(Span.PARENT_ID_NAME, Span.idToHex(30L)))
				.contains(new AbstractMap.SimpleEntry<String, String>(Span.SPAN_NAME_NAME, "anotherSpan"))
				.contains(new AbstractMap.SimpleEntry<String, String>("baggage-foo", "bar"));
	}

	private SpanTextMap textMap(Map<String, String> textMap) {
		return new SpanTextMap() {
			@Override public Iterator<Map.Entry<String, String>> iterator() {
				return textMap.entrySet().iterator();
			}

			@Override public void put(String key, String value) {
				textMap.put(key, value);
			}
		};
	}

}