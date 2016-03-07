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

package org.springframework.cloud.sleuth.instrument.web.client.feign;

import java.io.IOException;
import java.net.UnknownHostException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.cloud.netflix.feign.EnableFeignClients;
import org.springframework.cloud.netflix.feign.FeignClient;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.trace.TestSpanContextHolder;
import org.springframework.cloud.sleuth.util.ExceptionUtils;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import com.netflix.config.ConfigurationManager;
import com.netflix.hystrix.HystrixCommandProperties;

import static org.assertj.core.api.BDDAssertions.then;
import static org.junit.Assert.fail;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = { FeignTraceExceptionTests.TestConfiguration.class })
@DirtiesContext
public class FeignTraceExceptionTests {

	@Autowired
	TestFeignInterfaceWithException testFeignInterfaceWithException;

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
		Span span = this.tracer.createSpan("new trace");
		ConfigurationManager
				.getConfigInstance().setProperty("hystrix.command.shouldFailToConnect.execution.isolation.strategy",
				HystrixCommandProperties.ExecutionIsolationStrategy.SEMAPHORE);

		try {
			this.testFeignInterfaceWithException.shouldFailToConnect();
			fail("should throw an exception");
		} catch (Exception e) {
			then(e).hasRootCauseInstanceOf(UnknownHostException.class);
		}

		then(this.tracer.getCurrentSpan()).isEqualTo(span);
		this.tracer.close(span);
	}

	@FeignClient(name = "exceptionService", url = "http://invalid.host.to.break.tests")
	public interface TestFeignInterfaceWithException {
		@RequestMapping(method = RequestMethod.GET, value = "/")
		String shouldFailToConnect();
	}

	@Configuration
	@EnableAutoConfiguration
	@EnableFeignClients
	public static class TestConfiguration {

	}
}
