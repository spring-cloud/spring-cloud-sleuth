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

package org.springframework.cloud.sleuth.instrument.rsocket;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.CompositeByteBuf;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.sleuth.TraceContext;

public class TracingRequesterRSocketProxyTest {

	@Test
	public void checkNoLeaksOnDefaultTracingHeaders() {
		final TracingRequesterRSocketProxy tracingRequesterRSocketProxy = new TracingRequesterRSocketProxy(null, null,
				null, null, true);

		final ByteBufAllocator allocator = ByteBufAllocator.DEFAULT;
		final CompositeByteBuf metadata = allocator.compositeBuffer();
		tracingRequesterRSocketProxy.injectDefaultZipkinRSocketHeaders(metadata, new TraceContext() {
			@Override
			public String traceId() {
				return "0000000000000002";
			}

			@Override
			public String parentId() {
				return null;
			}

			@Override
			public String spanId() {
				return "0000000000000001";
			}

			@Override
			public Boolean sampled() {
				return null;
			}
		});

		// should add 2 components which are headers and body
		Assertions.assertThat(metadata.numComponents()).isEqualTo(2);
		Assertions.assertThat(metadata.refCnt()).isEqualTo(1);

		final ByteBuf c1 = metadata.internalComponent(0);
		final ByteBuf c2 = metadata.internalComponent(1);

		Assertions.assertThat(c1.refCnt()).isEqualTo(1);
		Assertions.assertThat(c2.refCnt()).isEqualTo(1);

		Assertions.assertThat(metadata.release()).isTrue();

		Assertions.assertThat(c1.refCnt()).isEqualTo(0);
		Assertions.assertThat(c2.refCnt()).isEqualTo(0);
	}

}
