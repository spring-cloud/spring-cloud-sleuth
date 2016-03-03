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

package org.springframework.cloud.sleuth.instrument.web.client;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.boot.test.WebIntegrationTest;
import org.springframework.cloud.netflix.feign.EnableFeignClients;
import org.springframework.cloud.netflix.feign.FeignClient;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.TraceKeys;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.assertions.SleuthAssertions;
import org.springframework.cloud.sleuth.instrument.hystrix.SleuthHystrixConcurrencyStrategy;
import org.springframework.cloud.sleuth.trace.TestSpanContextHolder;
import org.springframework.cloud.sleuth.util.ExceptionUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import com.jayway.awaitility.Awaitility;
import com.netflix.config.ConfigurationManager;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.SocketPolicy;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = { FeignTraceExceptionTests.TestConfiguration.class })
@DirtiesContext
public class FeignTraceExceptionTests {

	@Autowired
	TestFeignInterfaceWithException testFeignInterfaceWithException;

	@Autowired
	AssertingSleuthHystrixConcurrencyStrategy assertingSleuthHystrixConcurrencyStrategy;

	@Autowired
	Tracer tracer;

	@Before
	public void before() {
		ExceptionUtils.setFail(true);
	}

	@After
	public void close() {
		TestSpanContextHolder.removeCurrentSpan();
	}

	@Test
	public void shouldRemoveSpanFromThreadUponConnectionException() throws IOException {
		Span span = this.tracer.startTrace("new trace");

		try {
			this.testFeignInterfaceWithException.shouldFailToConnect();
			Assert.fail("should throw an exception");
		} catch (Exception e) {
			SleuthAssertions.then(e).hasRootCauseInstanceOf(TimeoutException.class);
		}

		SleuthAssertions.then(this.tracer.getCurrentSpan()).isEqualTo(span);
 		Awaitility.await().untilAtomic(this.assertingSleuthHystrixConcurrencyStrategy.successful,
				Matchers.is(true));
		this.tracer.close(span);
	}

	@FeignClient(name = "exceptionService", url = "http://asdasddaukdtkasudgajs.commmm")
	public interface TestFeignInterfaceWithException {
		@RequestMapping(method = RequestMethod.GET, value = "/nonExistentAddress")
		String shouldFailToConnect();
	}

	@Configuration
	@EnableAutoConfiguration
	@EnableFeignClients
	public static class TestConfiguration {

		@Bean
		SleuthHystrixConcurrencyStrategy assertingSleuthHystrixConcurrencyStrategy(Tracer tracer, TraceKeys traceKeys) {
			return new AssertingSleuthHystrixConcurrencyStrategy(tracer, traceKeys);
		}
	}

	public static class AssertingSleuthHystrixConcurrencyStrategy extends
			SleuthHystrixConcurrencyStrategy {

		private static final Log log = LogFactory.getLog(MethodHandles.lookup().lookupClass());

		AtomicBoolean successful = new AtomicBoolean(false);

		public AssertingSleuthHystrixConcurrencyStrategy(Tracer tracer,
				TraceKeys traceKeys) {
			super(tracer, traceKeys);
		}

		@Override public <T> Callable<T> wrapCallable(Callable<T> callable) {
			final Callable<T> wrapCallable = super.wrapCallable(callable);
			return () -> {
				try {
					T value = wrapCallable.call();
					if (Thread.currentThread().getName().contains("exceptionService")) {
						log.info("Value is [" + value + "]");
						AssertingSleuthHystrixConcurrencyStrategy.this.successful.set(true);
					} else {
						log.info("Current thread name is [" + Thread.currentThread().getName() + "]. We "
								+ "need to ensure that no illegal state exception due to not closing"
								+ "a span is not present in the exceptionService thread");
					}
					return value;
				} catch (Exception e) {
					if (Thread.currentThread().getName().contains("exceptionService")) {
						if ( e instanceof IllegalStateException) {
							if (!e.getMessage().contains("You may have forgotten to close or detach")) {
								log.info("Exception e [" + e + "] occurred in exceptionServiceThread");
								AssertingSleuthHystrixConcurrencyStrategy.this.successful.set(true);
								return null;
							}
							log.error("Exception occurred while trying to execute the callable", e);
							throw new AssertionError();
						} else {
							log.info("Exception e [" + e + "] occurred in exceptionServiceThread");
							AssertingSleuthHystrixConcurrencyStrategy.this.successful.set(true);
							return null;
						}
					}
					log.error("Exception occurred while trying to execute the callable", e);
					throw e;
				}

			};
		}
	}
}
