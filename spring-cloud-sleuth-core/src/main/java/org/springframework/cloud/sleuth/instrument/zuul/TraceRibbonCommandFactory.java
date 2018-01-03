/*
 * Copyright 2013-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.sleuth.instrument.zuul;

import org.springframework.cloud.netflix.ribbon.support.RibbonCommandContext;
import org.springframework.cloud.netflix.zuul.filters.route.RibbonCommand;
import org.springframework.cloud.netflix.zuul.filters.route.RibbonCommandFactory;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.instrument.web.HttpTraceKeysInjector;

/**
 * Propagates traces downstream via http headers that contain trace metadata.
 *
 * @author Spencer Gibb
 * @author Marcin Grzejszczak
 * @since 1.1.0
 */
class TraceRibbonCommandFactory implements RibbonCommandFactory {

	private final RibbonCommandFactory delegate;
	private final Tracer tracer;
	private final HttpTraceKeysInjector httpTraceKeysInjector;

	public TraceRibbonCommandFactory(RibbonCommandFactory delegate,
			Tracer tracer, HttpTraceKeysInjector httpTraceKeysInjector) {
		this.delegate = delegate;
		this.tracer = tracer;
		this.httpTraceKeysInjector = httpTraceKeysInjector;
	}

	@Override
	public RibbonCommand create(RibbonCommandContext context) {
		RibbonCommand ribbonCommand = this.delegate.create(context);
		Span span = this.tracer.getCurrentSpan();
		this.httpTraceKeysInjector.addRequestTags(span, context.uri(), context.getMethod());
		return ribbonCommand;
	}

}
