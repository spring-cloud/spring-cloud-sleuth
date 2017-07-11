package org.springframework.cloud.sleuth.instrument.web;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.springframework.cloud.sleuth.SpanTextMap;
import org.springframework.http.server.reactive.ServerHttpRequest;

/**
 * Created by mgrzejszczak.
 */
class ServerHttpRequestTextMap implements SpanTextMap {

	private final ServerHttpRequest delegate;
	private final Map<String, String> additionalHeaders = new HashMap<>();

	ServerHttpRequestTextMap(ServerHttpRequest delegate) {
		this.delegate = delegate;
		this.additionalHeaders.put(ZipkinHttpSpanExtractor.URI_HEADER,
				delegate.getPath().pathWithinApplication().value());
	}

	@Override
	public Iterator<Map.Entry<String, String>> iterator() {
		Map<String, String> map = new HashMap<>();
		for (Map.Entry<String, List<String>> entry : this.delegate.getHeaders()
				.entrySet()) {
			map.put(entry.getKey(), entry.getValue() != null ?
					entry.getValue().isEmpty() ? "" : entry.getValue().get(0) : "");
		}
		map.putAll(this.additionalHeaders);
		return map.entrySet().iterator();
	}

	@Override
	public void put(String key, String value) {
		this.additionalHeaders.put(key, value);
	}
}
