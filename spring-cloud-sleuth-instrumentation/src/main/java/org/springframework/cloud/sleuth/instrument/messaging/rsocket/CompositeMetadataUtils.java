package org.springframework.cloud.sleuth.instrument.messaging.rsocket;

import io.netty.buffer.ByteBuf;
import io.rsocket.metadata.CompositeMetadata;
import io.rsocket.metadata.CompositeMetadata.Entry;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import reactor.util.annotation.Nullable;

public class CompositeMetadataUtils {

  static Map<String, ByteBuf> extract(ByteBuf metadata, Set<String> keys) {
    final CompositeMetadata compositeMetadata = new CompositeMetadata(metadata, false);

    final Map<String, ByteBuf> map = new HashMap<>();
    for (Entry entry : compositeMetadata) {

      if (keys.isEmpty()) {
        return map;
      }

      final String key = entry.getMimeType();
      if (keys.contains(key)) {
        keys.remove(key);
        map.put(key, entry.getContent());
      }
    }

    return map;
  }

  @Nullable
  static ByteBuf extract(ByteBuf metadata, String key) {
    final CompositeMetadata compositeMetadata = new CompositeMetadata(metadata, false);

    for (Entry entry : compositeMetadata) {
      final String entryKey = entry.getMimeType();
      if (key.equals(entryKey)) {
        return entry.getContent();
      }
    }

    return null;
  }
}
