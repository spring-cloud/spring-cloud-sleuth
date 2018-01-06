package org.springframework.cloud.brave.instrument.web;

import brave.http.HttpClientParser;
import org.springframework.cloud.brave.TraceKeys;

/**
 * @author Marcin Grzejszczak
 * @since
 */
public class SleuthHttpClientParserAccessor {
	public static HttpClientParser get(TraceKeys traceKeys) {
		return new SleuthHttpClientParser(traceKeys);
	}
}
