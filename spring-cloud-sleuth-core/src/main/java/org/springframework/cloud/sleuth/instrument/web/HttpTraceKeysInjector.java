package org.springframework.cloud.sleuth.instrument.web;

import java.util.Collection;
import java.util.Map;

import org.springframework.cloud.sleuth.TraceKeys;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.util.StringUtils;

/**
 * Injects HTTP related keys to the current span.
 *
 * @author Marcin Grzejszczak
 *
 * @since 1.0.1
 */
public class HttpTraceKeysInjector {

	private final Tracer tracer;
	private final TraceKeys traceKeys;

	public HttpTraceKeysInjector(Tracer tracer, TraceKeys traceKeys) {
		this.tracer = tracer;
		this.traceKeys = traceKeys;
	}

	public void addRequestTags(String url, String host, String path, String method) {
		this.tracer.addTag(this.traceKeys.getHttp().getUrl(), url);
		this.tracer.addTag(this.traceKeys.getHttp().getHost(), host);
		this.tracer.addTag(this.traceKeys.getHttp().getPath(), path);
		this.tracer.addTag(this.traceKeys.getHttp().getMethod(), method);
	}

	public void addRequestTags(String url, String host, String path, String method,
			Map<String, ? extends Collection<String>> headers) {
		addRequestTags(url, host, path, method);
		addRequestTagsFromHeaders(headers);
	}

	private void addRequestTagsFromHeaders(Map<String, ? extends Collection<String>> headers) {
		for (String name : this.traceKeys.getHttp().getHeaders()) {
			for (Map.Entry<String, ? extends Collection<String>> entry : headers.entrySet()) {
				addTagForEntry(name, entry.getValue());
			}
		}
	}

	private void addTagForEntry(String name, Collection<String> list) {
		String key = this.traceKeys.getHttp().getPrefix() + name.toLowerCase();
		String value = list.size() == 1 ? list.iterator().next()
				: StringUtils.collectionToDelimitedString(list, ",", "'", "'");
		this.tracer.addTag(key, value);
	}

}
