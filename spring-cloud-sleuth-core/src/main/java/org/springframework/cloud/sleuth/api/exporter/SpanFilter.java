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

package org.springframework.cloud.sleuth.api.exporter;

/**
 * An interface that allows to filter whether a given reported span should be exported or
 * not.
 */
public interface SpanFilter {

	/**
	 * Called to export sampled {@code Span}s.
	 * @param span the collection of sampled Spans to be exported.
	 * @return whether should export spans
	 */
	boolean isExportable(ReportedSpan span);

}
