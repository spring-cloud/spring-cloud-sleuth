package org.springframework.cloud.sleuth.instrument.messaging.rsocket;

import io.netty.buffer.ByteBuf;
import io.netty.util.CharsetUtil;
import io.rsocket.metadata.CompositeMetadata;
import io.rsocket.metadata.CompositeMetadata.Entry;
import org.springframework.cloud.sleuth.propagation.Propagator;

class ByteBufGetter implements Propagator.Getter<ByteBuf> {

	@Override
	public String get(ByteBuf carrier, String key) {
		final CompositeMetadata compositeMetadata = new CompositeMetadata(carrier, false);

		for (Entry entry : compositeMetadata) {
			if (key.equals(entry.getMimeType())) {
				return entry.getContent().toString(CharsetUtil.UTF_8);
			}
		}

		return null;
	}

}
