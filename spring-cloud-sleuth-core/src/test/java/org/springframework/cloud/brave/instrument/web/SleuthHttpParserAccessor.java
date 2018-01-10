package org.springframework.cloud.brave.instrument.web;

import brave.http.HttpClientParser;
import brave.http.HttpServerParser;
import org.springframework.cloud.brave.ErrorParser;
import org.springframework.cloud.brave.TraceKeys;

/**
 * @author Marcin Grzejszczak
 * @since
 */
public class SleuthHttpParserAccessor {
	public static HttpClientParser getClient(TraceKeys traceKeys) {
		return new SleuthHttpClientParser(traceKeys);
	}

	public static HttpServerParser getServer(TraceKeys traceKeys, ErrorParser errorParser) {
		return new SleuthHttpServerParser(traceKeys, errorParser);
	}
}
