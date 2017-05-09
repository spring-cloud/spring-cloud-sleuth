package org.springframework.cloud.sleuth;

import org.junit.Test;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author Marcin Grzejszczak
 */
public class ExceptionMessageErrorParserTests {

	@Test
	public void should_return_exception_message() throws Exception {
		Throwable e = new RuntimeException("foo");

		String msg = new ExceptionMessageErrorParser().parseError(e);

		then(msg).isEqualTo("foo");
	}

}