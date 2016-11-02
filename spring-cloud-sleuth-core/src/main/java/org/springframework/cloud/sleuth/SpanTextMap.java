package org.springframework.cloud.sleuth;

import java.util.Iterator;
import java.util.Map;

/**
 * Adopted from: https://github.com/opentracing/opentracing-java/blob/master/opentracing-api/src/main/java/io/opentracing/propagation/TextMap.java
 *
 * TextMap is a built-in carrier for {@link SpanInjector} and {@link SpanExtractor}. TextMap implementations allows Tracers to
 * read and write key:value String pairs from arbitrary underlying sources of data.
 *
 * @author Marcin Grzejszczak
 * @since 1.2.0
 */
public interface SpanTextMap extends Iterable<Map.Entry<String, String>> {
	/**
	 * Gets an iterator over arbitrary key:value pairs from the TextMapReader.
	 *
	 * @return entries in the TextMap backing store; note that for some Formats, the iterator may include entries that
	 * were never injected by a Tracer implementation (e.g., unrelated HTTP headers)
	 */
	Iterator<Map.Entry<String,String>> iterator();

	/**
	 * Puts a key:value pair into the TextMapWriter's backing store.
	 *
	 * @param key a String, possibly with constraints dictated by the particular Format this TextMap is paired with
	 * @param value a String, possibly with constraints dictated by the particular Format this TextMap is paired with
	 */
	void put(String key, String value);
}