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

import io.netty.buffer.ByteBuf;
import io.rsocket.metadata.CompositeMetadata;
import io.rsocket.metadata.CompositeMetadata.Entry;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import reactor.util.annotation.Nullable;

class CompositeMetadataUtils {

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
