/*
 * Copyright 2013-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.sleuth.util;

/**
 * Utility class that provides the name in hyphen based notation
 *
 * @author Adrian Cole
 * @since 1.0.2
 */
public final class SpanNameUtil {

	public static String toLowerHyphen(String name) {
		StringBuilder result = new StringBuilder();
		for (int i = 0; i < name.length(); i++) {
			char c = name.charAt(i);
			if (isUppercase(c)) {
				if (i != 0) result.append('-');
				result.append(toLowerCase(c));
			} else {
				result.append(c);
			}
		}
		return result.toString();
	}

	private static char toLowerCase(char c) {
		return (char) (c + 'a' - 'A');
	}

	private static boolean isUppercase(char c) {
		return c >= 'A' && c <= 'Z';
	}
}
