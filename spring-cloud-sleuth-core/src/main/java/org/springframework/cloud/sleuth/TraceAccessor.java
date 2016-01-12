/*
 * Copyright 2015 the original author or authors.
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

package org.springframework.cloud.sleuth;

/**
 * Strategy for accessing the current span. This is the primary interface for use by user
 * code (if it needs access to spans at all - in general it is better to leave span access
 * to specialized and cross-cutting instrumentation code).
 *
 * @author Dave Syer
 *
 */
public interface TraceAccessor {

	Span getCurrentSpan();

	boolean isTracing();

}
