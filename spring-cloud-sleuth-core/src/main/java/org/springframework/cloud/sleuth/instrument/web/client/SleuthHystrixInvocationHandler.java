/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.cloud.sleuth.instrument.web.client;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Map;

import org.springframework.cloud.sleuth.TraceKeys;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.instrument.hystrix.TraceCommand;
import org.springframework.util.ReflectionUtils;

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;

import feign.InvocationHandlerFactory;
import feign.InvocationHandlerFactory.MethodHandler;
import feign.Target;

import static feign.Util.checkNotNull;

/**
 * Wraps {@link HystrixCommand} execution in Sleuth's {@link TraceCommand}
 *
 * @since 1.0.0
 */
final class SleuthHystrixInvocationHandler implements InvocationHandler {

	private final Target<?> target;
	private final Map<Method, MethodHandler> dispatch;
	private final Tracer tracer;
	private final TraceKeys traceKeys;

	SleuthHystrixInvocationHandler(Target<?> target, Map<Method, MethodHandler> dispatch,
			Tracer tracer, TraceKeys traceKeys) {
		this.tracer = checkNotNull(tracer, "traceManager");
		this.target = checkNotNull(target, "target");
		this.dispatch = checkNotNull(dispatch, "dispatch");
		this.traceKeys = checkNotNull(traceKeys, "traceKeys");
	}

	@Override public Object invoke(final Object proxy, final Method method,
			final Object[] args) throws Throwable {
		String groupKey = this.target.name();
		String commandKey = method.getName();
		HystrixCommand.Setter setter = HystrixCommand.Setter
				.withGroupKey(HystrixCommandGroupKey.Factory.asKey(groupKey))
				.andCommandKey(HystrixCommandKey.Factory.asKey(commandKey));
		HystrixCommand<Object> hystrixCommand = new TraceCommand<Object>(this.tracer, this.traceKeys,
				setter) {
			@Override public Object doRun() throws Exception {
				try {
					return SleuthHystrixInvocationHandler.this.dispatch.get(method)
							.invoke(args);
				}
				catch (Throwable throwable) {
					ReflectionUtils.rethrowException(throwable);
				}
				return null;
			}
		};
		if (HystrixCommand.class.isAssignableFrom(method.getReturnType())) {
			return hystrixCommand;
		}
		return hystrixCommand.execute();
	}

	static final class Factory implements InvocationHandlerFactory {

		private final Tracer tracer;
		private final TraceKeys traceKeys;

		public Factory(Tracer tracer, TraceKeys traceKeys) {
			this.tracer = tracer;
			this.traceKeys = traceKeys;
		}

		@Override public InvocationHandler create(
				@SuppressWarnings("rawtypes") Target target,
				Map<Method, MethodHandler> dispatch) {
			return new SleuthHystrixInvocationHandler(target, dispatch, this.tracer,
					this.traceKeys);
		}
	}
}