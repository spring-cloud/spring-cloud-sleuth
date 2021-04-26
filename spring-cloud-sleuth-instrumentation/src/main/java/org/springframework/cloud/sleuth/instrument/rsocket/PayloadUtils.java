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

package org.springframework.cloud.sleuth.instrument.rsocket;

import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.CompositeByteBuf;
import io.rsocket.Payload;
import io.rsocket.metadata.CompositeMetadata;
import io.rsocket.metadata.CompositeMetadata.Entry;
import io.rsocket.metadata.CompositeMetadataCodec;
import io.rsocket.util.ByteBufPayload;
import io.rsocket.util.DefaultPayload;
import java.util.Set;

class PayloadUtils {

	static Payload cleanTracingMetadata(Payload payload, Set<String> fields) {
		final CompositeMetadata entries = new CompositeMetadata(payload.metadata(), true);
		final CompositeByteBuf metadata = ByteBufAllocator.DEFAULT.compositeBuffer();

		for (Entry entry : entries) {
			if (!fields.contains(entry.getMimeType())) {
				CompositeMetadataCodec.encodeAndAddMetadataWithCompression(metadata, ByteBufAllocator.DEFAULT,
						entry.getMimeType(), entry.getContent());
			}
		}

		final Payload newPayload;
		if (payload instanceof ByteBufPayload) {
			newPayload = ByteBufPayload.create(payload.data().retain(), metadata.retain());
			payload.release();
		}
		else {
			newPayload = DefaultPayload.create(payload.data().retain(), metadata.retain());
			payload.release();
		}

		return newPayload;
	}

}
