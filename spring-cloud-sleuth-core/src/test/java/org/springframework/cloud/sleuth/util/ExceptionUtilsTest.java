/*
 * Copyright 2013-2017 the original author or authors.
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

package org.springframework.cloud.sleuth.util;

import org.junit.After;
import org.junit.Test;

import static org.assertj.core.api.BDDAssertions.then;
import static org.junit.Assert.fail;

/**
 * @author Marcin Grzejszczak
 */
public class ExceptionUtilsTest {

	@After
	public void clean() {
		ExceptionUtils.setFail(true);
	}

	@Test
	public void should_not_throw_exception_if_flag_is_disabled() throws Exception {
		ExceptionUtils.setFail(false);

		ExceptionUtils.warn("warning message");
	}

	@Test
	public void should_throw_exception_if_flag_is_disabled() throws Exception {
		ExceptionUtils.setFail(true);

		try {
			ExceptionUtils.warn("warning message");
			fail("should throw an exception");
		} catch (Exception e) {
			then(e).isInstanceOf(IllegalStateException.class);
		}
	}

	@Test
	public void should_print_error_message_when_there_is_one() throws Exception {
		Throwable e = new RuntimeException("Foo");

		String message = ExceptionUtils.getExceptionMessage(e);

		then(message).isEqualTo("Foo");
	}

	@Test
	public void should_print_to_string_when_there_is_no_error() throws Exception {
		Throwable e = new RuntimeException();

		String message = ExceptionUtils.getExceptionMessage(e);

		then(message).isEqualTo("java.lang.RuntimeException");
	}
}