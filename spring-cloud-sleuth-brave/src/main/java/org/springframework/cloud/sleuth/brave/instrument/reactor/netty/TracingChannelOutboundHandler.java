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

import java.net.SocketAddress;

import brave.Span;
import brave.propagation.CurrentTraceContext;
import brave.propagation.TraceContext;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.util.AttributeKey;
import reactor.netty.Connection;
import reactor.netty.ConnectionObserver;
import reactor.netty.channel.ChannelOperations;
import reactor.netty.http.client.HttpClientRequest;

/**
 * {@link ChannelOutboundHandlerAdapter} that wraps all events in scope.
 * <p>
 * WARNING: Using this feature can lead to serious performance issues. This should be only
 * used for debugging purposes.
 *
 * @since 3.1.9
 */
public class TracingChannelOutboundHandler extends ChannelOutboundHandlerAdapter {

	static final AttributeKey<Span> SPAN_ATTRIBUTE_KEY = AttributeKey.valueOf(Span.class.getName());

	final CurrentTraceContext currentTraceContext;

	/**
	 * Creates a new instance of {@link TracingChannelOutboundHandler}.
	 * @param currentTraceContext current trace context
	 */
	public TracingChannelOutboundHandler(CurrentTraceContext currentTraceContext) {
		this.currentTraceContext = currentTraceContext;
	}

	@Override
	public void bind(ChannelHandlerContext ctx, SocketAddress localAddress, ChannelPromise promise) {
		if (instrumentOperation(ctx, () -> ctx.bind(localAddress, promise))) {
			return;
		}

		ctx.bind(localAddress, promise);
	}

	@Override
	public void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress, SocketAddress localAddress,
			ChannelPromise promise) {
		if (instrumentOperation(ctx, () -> ctx.connect(remoteAddress, localAddress, promise))) {
			return;
		}

		ctx.connect(remoteAddress, localAddress, promise);
	}

	@Override
	public void disconnect(ChannelHandlerContext ctx, ChannelPromise promise) {
		if (instrumentOperation(ctx, () -> ctx.disconnect(promise))) {
			return;
		}

		ctx.disconnect(promise);
	}

	@Override
	public void close(ChannelHandlerContext ctx, ChannelPromise promise) {
		if (instrumentOperation(ctx, () -> ctx.close(promise))) {
			return;
		}

		ctx.close(promise);
	}

	@Override
	public void deregister(ChannelHandlerContext ctx, ChannelPromise promise) {
		if (instrumentOperation(ctx, () -> ctx.deregister(promise))) {
			return;
		}

		ctx.deregister(promise);
	}

	@Override
	public void read(ChannelHandlerContext ctx) {
		if (instrumentOperation(ctx, () -> ctx.read())) {
			return;
		}

		ctx.read();
	}

	@Override
	public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
		if (instrumentOperation(ctx, () -> ctx.write(msg, promise))) {
			return;
		}

		ctx.write(msg, promise);
	}

	@Override
	public void flush(ChannelHandlerContext ctx) {
		if (instrumentOperation(ctx, () -> ctx.flush())) {
			return;
		}

		ctx.flush();
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
				if (ops instanceof HttpClientRequest) {
					TraceContext traceContext = TracingHandlerUtil
							.traceContext(((HttpClientRequest) ops).currentContextView());
					if (traceContext != null) {
						try (CurrentTraceContext.Scope scope = currentTraceContext.maybeScope(traceContext)) {
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
