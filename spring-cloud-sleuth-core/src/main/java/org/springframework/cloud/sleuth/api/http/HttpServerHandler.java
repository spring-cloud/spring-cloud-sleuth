package org.springframework.cloud.sleuth.api.http;

import org.springframework.cloud.sleuth.api.Span;

public interface HttpServerHandler  {
	Span handleReceive(HttpServerRequest request);

	void handleSend(HttpServerResponse response, Span span);
}
