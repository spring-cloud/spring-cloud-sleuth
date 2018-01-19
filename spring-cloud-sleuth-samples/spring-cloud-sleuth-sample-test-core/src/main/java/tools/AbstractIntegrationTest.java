/*
 * Copyright 2013-2018 the original author or authors.
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

import java.lang.invoke.MethodHandles;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;
import org.springframework.web.client.RestTemplate;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * @author Marcin Grzejszczak
 */
public abstract class AbstractIntegrationTest {

	protected static final Log log = LogFactory.getLog(MethodHandles.lookup().lookupClass());

	protected static final int POLL_INTERVAL = 1;
	protected static final int TIMEOUT = 20;
	protected final RestTemplate restTemplate = new AssertingRestTemplate();


	protected Runnable httpMessageWithTraceIdInHeadersIsSuccessfullySent(String endpoint, long traceId) {
		return new RequestSendingRunnable(this.restTemplate, endpoint, traceId, traceId);
	}

	protected Runnable httpMessageWithTraceIdInHeadersIsSuccessfullySent(String endpoint, long traceId, Long spanId) {
		return new RequestSendingRunnable(this.restTemplate, endpoint, traceId, spanId);
	}

	public static ConditionFactory await() {
		return Awaitility.await().pollInterval(POLL_INTERVAL, SECONDS).atMost(TIMEOUT, SECONDS);
	}
}
