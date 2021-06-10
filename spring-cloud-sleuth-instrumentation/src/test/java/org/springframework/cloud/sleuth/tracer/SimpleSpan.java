/*
 * Copyright 2013-2021 the original author or authors.
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

package org.springframework.cloud.sleuth.tracer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.TraceContext;

/**
 * A noop implementation. Does nothing.
 *
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
public class SimpleSpan implements Span {

	public Map<String, String> tags = new HashMap<>();

	public boolean started;

	public boolean ended;

	public Throwable throwable;

	public String remoteServiceName;

	public Span.Kind spanKind;

	public List<String> events = new ArrayList<>();

	public String name;

	public String ip;

	public int port;

	@Override
	public boolean isNoop() {
		return true;
	}

	@Override
	public TraceContext context() {
		return new NoOpTraceContext();
	}

	@Override
	public SimpleSpan start() {
		this.started = true;
		return this;
	}

	@Override
	public SimpleSpan name(String name) {
		this.name = name;
		return this;
	}

	@Override
	public SimpleSpan event(String value) {
		this.events.add(value);
		return this;
	}

	@Override
	public SimpleSpan tag(String key, String value) {
		this.tags.put(key, value);
		return this;
	}

	@Override
	public SimpleSpan error(Throwable throwable) {
		this.throwable = throwable;
		return this;
	}

	@Override
	public Span remoteIpAndPort(String ip, int port) {
		this.ip = ip;
		this.port = port;
		return this;
	}

	@Override
	public void end() {
		this.ended = true;
	}

	@Override
	public void abandon() {

	}

	@Override
	public Span remoteServiceName(String remoteServiceName) {
		this.remoteServiceName = remoteServiceName;
		return this;
	}

	@Override
	public String toString() {
		return "SimpleSpan{" + "tags=" + tags + ", started=" + started + ", ended=" + ended + ", throwable=" + throwable
				+ ", remoteServiceName='" + remoteServiceName + '\'' + ", spanKind=" + spanKind + ", events=" + events
				+ ", name='" + name + '\'' + '}';
	}

}
