package org.springframework.cloud.sleuth.util;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author Marcin Grzejszczak
 */
public class TextMapUtilTests {

	@Test
	public void should_convert_an_iterable_to_a_caseinsensitive_map() throws Exception {
		List<Map.Entry<String, String>> iterable = new ArrayList<>();
		iterable.add(new AbstractMap.SimpleEntry<>("foo", "bar"));

		Map<String, String> map = TextMapUtil.asMap(iterable);

		then(map)
				.containsKey("foo")
				.containsKey("FOO")
				.containsKey("FoO")
				.contains(new AbstractMap.SimpleEntry<>("foo", "bar"));
	}

}