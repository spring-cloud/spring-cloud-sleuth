/*
 * Copyright 2013-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.sleuth.instrument.web.mvc;

import org.springframework.cloud.sleuth.CurrentTraceContext;
import org.springframework.cloud.sleuth.http.HttpServerHandler;
import org.springframework.context.ApplicationContext;
import org.springframework.web.method.HandlerMethod;

/**
 * Access to Spring WebMvc version-specific features.
 *
 * <p>
 * Originally designed by OkHttp team, derived from
 * {@code okhttp3.internal.platform.Platform}
 */
abstract class WebMvcRuntime {

	private static final WebMvcRuntime WEBMVC_RUNTIME = findWebMvcRuntime();

	abstract CurrentTraceContext currentTraceContext(ApplicationContext ctx);

	abstract HttpServerHandler httpServerHandler(ApplicationContext ctx);

	abstract boolean isHandlerMethod(Object handler);

	WebMvcRuntime() {
	}

	static WebMvcRuntime get() {
		return WEBMVC_RUNTIME;
	}

	/** Attempt to match the host runtime to a capable Platform implementation. */
	static WebMvcRuntime findWebMvcRuntime() {
		// Find spring-webmvc v3.1 new methods
		try {
			Class.forName("org.springframework.web.method.HandlerMethod");
			return new WebMvc31(); // intentionally doesn't not access the type prior to
									// the above guard
		}
		catch (ClassNotFoundException e) {
			// pre spring-webmvc v3.1
		}

		throw new UnsupportedOperationException("Pre Spring Web 3.1 not supported");
	}

	static final class WebMvc31 extends WebMvcRuntime {

		@Override
		CurrentTraceContext currentTraceContext(ApplicationContext ctx) {
			return ctx.getBean(CurrentTraceContext.class);
		}

		@Override
		HttpServerHandler httpServerHandler(ApplicationContext ctx) {
			return ctx.getBean(HttpServerHandler.class);
		}

		@Override
		boolean isHandlerMethod(Object handler) {
			return handler instanceof HandlerMethod;
		}

	}

}
