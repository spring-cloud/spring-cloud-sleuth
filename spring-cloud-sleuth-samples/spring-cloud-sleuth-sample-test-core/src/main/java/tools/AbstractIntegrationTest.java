/*
 * Copyright 2013-2015 the original author or authors.
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
package tools;

import com.jayway.awaitility.Awaitility;
import com.jayway.awaitility.core.ConditionFactory;
import org.junit.experimental.categories.Category;
import org.springframework.beans.factory.annotation.Value;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * @author Marcin Grzejszczak
 */
@Category(DockerTests.class)
abstract public class AbstractIntegrationTest {

	@Value("${test.pollinterval:1}") protected int pollInterval;
	@Value("${test.timeout:10}") protected int timeout;

	protected ConditionFactory await() {
		return Awaitility.await().pollInterval(pollInterval, SECONDS).atMost(timeout, SECONDS);
	}

	protected long zipkinHashedTraceId(String string) {
		long h = 1125899906842597L;
		if (string == null) {
			return h;
		}
		int len = string.length();

		for (int i = 0; i < len; i++) {
			h = 31 * h + string.charAt(i);
		}
		return h;
	}
}
