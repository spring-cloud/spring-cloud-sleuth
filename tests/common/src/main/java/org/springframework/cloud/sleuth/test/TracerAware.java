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

package org.springframework.cloud.sleuth.test;

import org.springframework.cloud.sleuth.api.CurrentTraceContext;
import org.springframework.cloud.sleuth.api.Tracer;
import org.springframework.cloud.sleuth.api.http.HttpClientHandler;
import org.springframework.cloud.sleuth.api.http.HttpServerHandler;
import org.springframework.cloud.sleuth.api.propagation.Propagator;

public interface TracerAware {

	Tracer tracer();

	CurrentTraceContext currentTraceContext();

	Propagator propagator();

	HttpServerHandler httpServerHandler();

	HttpClientHandler httpClientHandler();

	/*
	 * public MutableSpan takeLocalSpan() { MutableSpan result = doTakeSpan(false);
	 * assertThat(result.kind()) .withFailMessage("Expected %s to have no kind", result)
	 * .isNull(); assertThat(result.remoteServiceName())
	 * .withFailMessage("Expected %s to have no remote endpoint", result) .isNull();
	 * return result; }
	 */

}
