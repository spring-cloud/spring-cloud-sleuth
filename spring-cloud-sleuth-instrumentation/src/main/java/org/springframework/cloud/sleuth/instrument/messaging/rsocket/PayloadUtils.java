package org.springframework.cloud.sleuth.instrument.messaging.rsocket;

import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.CompositeByteBuf;
import io.rsocket.Payload;
import io.rsocket.metadata.CompositeMetadata;
import io.rsocket.metadata.CompositeMetadata.Entry;
import io.rsocket.metadata.CompositeMetadataCodec;
import io.rsocket.util.ByteBufPayload;
import io.rsocket.util.DefaultPayload;
import java.util.Set;

public class PayloadUtils {

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
