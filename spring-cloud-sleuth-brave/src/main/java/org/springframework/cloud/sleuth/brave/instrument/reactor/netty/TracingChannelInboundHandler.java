/*
 * Copyright 2013-2023 the original author or authors.
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

package org.springframework.cloud.sleuth.brave.instrument.reactor.netty;

import brave.Span;
import brave.propagation.CurrentTraceContext;
import brave.propagation.TraceContext;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.AttributeKey;
import reactor.netty.Connection;
import reactor.netty.ConnectionObserver;
import reactor.netty.channel.ChannelOperations;
import reactor.netty.http.client.HttpClientResponse;

/**
 * {@link ChannelInboundHandlerAdapter} that wraps all events in scope.
 * <p>
 * WARNING: Using this feature can lead to serious performance issues. This should be only
 * used for debugging purposes.
 *
 * @since 3.1.9
 */
public class TracingChannelInboundHandler extends ChannelInboundHandlerAdapter {

	static final AttributeKey<Span> SPAN_ATTRIBUTE_KEY = AttributeKey.valueOf(Span.class.getName());

	final CurrentTraceContext currentTraceContext;

	/**
	 * Creates a new instance of {@link TracingChannelInboundHandler}.
	 * @param currentTraceContext current trace context
	 */
	public TracingChannelInboundHandler(CurrentTraceContext currentTraceContext) {
		this.currentTraceContext = currentTraceContext;
	}

	@Override
	public void channelRegistered(ChannelHandlerContext ctx) {
		if (instrumentOperation(ctx, () -> ctx.fireChannelRegistered())) {
			return;
		}

		ctx.fireChannelRegistered();
	}

	@Override
	public void channelUnregistered(ChannelHandlerContext ctx) {
		if (instrumentOperation(ctx, () -> ctx.fireChannelUnregistered())) {
			return;
		}

		ctx.fireChannelUnregistered();
	}

	@Override
	public void channelActive(ChannelHandlerContext ctx) {
		if (instrumentOperation(ctx, () -> ctx.fireChannelActive())) {
			return;
		}

		ctx.fireChannelActive();
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) {
		if (instrumentOperation(ctx, () -> ctx.fireChannelInactive())) {
			return;
		}

		ctx.fireChannelInactive();
	}

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) {
		if (instrumentOperation(ctx, () -> ctx.fireChannelRead(msg))) {
			return;
		}

		ctx.fireChannelRead(msg);
	}

	@Override
	public void channelReadComplete(ChannelHandlerContext ctx) {
		if (instrumentOperation(ctx, () -> ctx.fireChannelReadComplete())) {
			return;
		}

		ctx.fireChannelReadComplete();
	}

	@Override
	public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
		if (instrumentOperation(ctx, () -> ctx.fireUserEventTriggered(evt))) {
			return;
		}

		ctx.fireUserEventTriggered(evt);
	}

	@Override
	public void channelWritabilityChanged(ChannelHandlerContext ctx) {
		if (instrumentOperation(ctx, () -> ctx.fireChannelWritabilityChanged())) {
			return;
		}

		ctx.fireChannelWritabilityChanged();
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
		if (instrumentOperation(ctx, () -> ctx.fireExceptionCaught(cause))) {
			return;
		}

		ctx.fireExceptionCaught(cause);
	}

	@Override
	public boolean isSharable() {
		return false;
	}

	@Override
	public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
		if (instrumentOperation(ctx, () -> {
			try {
				super.handlerAdded(ctx);
			}
			catch (Exception e) {
				throw new RuntimeException(e);
			}
		})) {
			return;
		}

		super.handlerAdded(ctx);
	}

	@Override
	public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
		if (instrumentOperation(ctx, () -> {
			try {
				super.handlerRemoved(ctx);
			}
			catch (Exception e) {
				throw new RuntimeException(e);
			}
		})) {
			return;
		}

		super.handlerRemoved(ctx);
	}

	boolean instrumentOperation(ChannelHandlerContext ctx, Runnable operation) {
		Span span = ctx.channel().attr(SPAN_ATTRIBUTE_KEY).get();
		if (span != null) {
			try (CurrentTraceContext.Scope scope = currentTraceContext.maybeScope(span.context())) {
				operation.run();
			}
			return true;
		}
		else {
			Connection conn = Connection.from(ctx.channel());
			if (conn instanceof ConnectionObserver) {
				TraceContext parent = ((ConnectionObserver) conn).currentContext().getOrDefault(TraceContext.class,
						null);
				if (parent != null) {
					try (CurrentTraceContext.Scope scope = currentTraceContext.maybeScope(parent)) {
						operation.run();
					}
					return true;
				}
			}
			else {
				ChannelOperations<?, ?> ops = conn.as(ChannelOperations.class);
				if (ops instanceof HttpClientResponse) {
					TraceContext parent = TracingHandlerUtil
							.traceContext(((HttpClientResponse) ops).currentContextView());
					if (parent != null) {
						try (CurrentTraceContext.Scope scope = currentTraceContext.maybeScope(parent)) {
							operation.run();
						}
						return true;
					}
				}
			}
		}
		return false;
	}

}
