/*
 * Copyright 2012-2015 the original author or authors.
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
package org.springframework.cloud.sleuth.correlation;

/**
 * Component that stores correlation id using {@link ThreadLocal}
 *
 * @author Jakub Nabrdalik, 4financeIT
 * @author Marcin Zajaczkowski, 4financeIT
 */
public class CorrelationIdHolder {
	public static final String CORRELATION_ID_HEADER = "Correlation-Id";
	private static final ThreadLocal<String> id = new ThreadLocal<String>();

	public static void set(String correlationId) {
		id.set(correlationId);
	}

	public static String get() {
		return id.get();
	}

	public static void remove() {
		id.remove();
	}
}
