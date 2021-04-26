package org.springframework.cloud.sleuth.instrument.messaging.rsocket;

import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.CompositeByteBuf;
import io.rsocket.metadata.CompositeMetadataCodec;
import org.springframework.cloud.sleuth.propagation.Propagator;

class ByteBufSetter implements Propagator.Setter<CompositeByteBuf> {

	@Override
	public void set(CompositeByteBuf carrier, String key, String value) {
		final ByteBufAllocator alloc = carrier.alloc();
		CompositeMetadataCodec.encodeAndAddMetadataWithCompression(carrier, alloc, key,
				ByteBufUtil.writeUtf8(alloc, value));
	}

}
