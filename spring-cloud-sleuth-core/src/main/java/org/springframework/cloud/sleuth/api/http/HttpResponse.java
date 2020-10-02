package org.springframework.cloud.sleuth.api.http;

import org.springframework.lang.Nullable;

public interface HttpResponse extends Response {

	@Nullable
	default HttpRequest request() {
		return null;
	}

	@Nullable
	default String method() {
		HttpRequest request = request();
		return request != null ? request.method() : null;
	}

	@Nullable
	default String route() {
		HttpRequest request = request();
		return request != null ? request.route() : null;
	}

	/**
	 * The HTTP status code or zero if unreadable.
	 *
	 * <p>
	 * Conventionally associated with the key "http.status_code"
	 *
	 * @since 5.10
	 */
	int statusCode();

}
