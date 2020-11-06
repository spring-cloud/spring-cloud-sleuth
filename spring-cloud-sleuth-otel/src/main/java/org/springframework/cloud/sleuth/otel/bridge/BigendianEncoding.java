/*
 * Copyright 2013-2020 the original author or authors.
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

package org.springframework.cloud.sleuth.otel.bridge;

import java.util.Arrays;

import io.opentelemetry.api.internal.Utils;

/**
 * Copied from io.opentelemetry.api.trace.BigendianEncoding.
 */
final class BigendianEncoding {

	static final int LONG_BYTES = Long.SIZE / Byte.SIZE;
	static final int BYTE_BASE16 = 2;
	static final int LONG_BASE16 = BYTE_BASE16 * LONG_BYTES;

	private static final String ALPHABET = "0123456789abcdef";

	private static final int ASCII_CHARACTERS = 128;

	private static final byte[] DECODING = buildDecodingArray();

	private BigendianEncoding() {
	}

	private static byte[] buildDecodingArray() {
		byte[] decoding = new byte[ASCII_CHARACTERS];
		Arrays.fill(decoding, (byte) -1);
		for (int i = 0; i < ALPHABET.length(); i++) {
			char c = ALPHABET.charAt(i);
			decoding[c] = (byte) i;
		}
		return decoding;
	}

	/**
	 * Returns the {@code long} value whose base16 representation is stored in the first
	 * 16 chars of {@code chars} starting from the {@code offset}.
	 * @param chars the base16 representation of the {@code long}.
	 */
	static long longFromBase16String(CharSequence chars) {
		return longFromBase16String(chars, 0);
	}

	/**
	 * Returns the {@code long} value whose base16 representation is stored in the first
	 * 16 chars of {@code chars} starting from the {@code offset}.
	 * @param chars the base16 representation of the {@code long}.
	 */
	static long longFromBase16String(CharSequence chars, int offset) {
		Utils.checkArgument(chars.length() >= offset + LONG_BASE16, "chars too small");
		return (decodeByte(chars.charAt(offset), chars.charAt(offset + 1)) & 0xFFL) << 56
				| (decodeByte(chars.charAt(offset + 2), chars.charAt(offset + 3)) & 0xFFL) << 48
				| (decodeByte(chars.charAt(offset + 4), chars.charAt(offset + 5)) & 0xFFL) << 40
				| (decodeByte(chars.charAt(offset + 6), chars.charAt(offset + 7)) & 0xFFL) << 32
				| (decodeByte(chars.charAt(offset + 8), chars.charAt(offset + 9)) & 0xFFL) << 24
				| (decodeByte(chars.charAt(offset + 10), chars.charAt(offset + 11)) & 0xFFL) << 16
				| (decodeByte(chars.charAt(offset + 12), chars.charAt(offset + 13)) & 0xFFL) << 8
				| (decodeByte(chars.charAt(offset + 14), chars.charAt(offset + 15)) & 0xFFL);
	}

	private static byte decodeByte(char hi, char lo) {
		Utils.checkArgument(lo < ASCII_CHARACTERS && DECODING[lo] != -1, "invalid character " + lo);
		Utils.checkArgument(hi < ASCII_CHARACTERS && DECODING[hi] != -1, "invalid character " + hi);
		int decoded = DECODING[hi] << 4 | DECODING[lo];
		return (byte) decoded;
	}

}
