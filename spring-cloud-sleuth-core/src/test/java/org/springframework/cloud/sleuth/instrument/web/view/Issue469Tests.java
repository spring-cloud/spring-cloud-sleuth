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

package org.springframework.cloud.sleuth.instrument.web.view;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.boot.test.WebIntegrationTest;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.util.ExceptionUtils;
import org.springframework.core.env.Environment;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.BDDAssertions.then;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = Issue469.class)
@TestPropertySource(properties = {"spring.mvc.view.prefix=/WEB-INF/jsp/",
		"spring.mvc.view.suffix=.jsp"})
@WebIntegrationTest({ "server.port=0" })
public class Issue469Tests {

	@Autowired Tracer tracer;
	@Autowired Environment environment;
	RestTemplate restTemplate = new RestTemplate();

	@Before
	public void setup() {
		ExceptionUtils.setFail(true);
	}

	@Test
	public void should_not_result_in_tracing_exceptions_when_using_view_controllers() throws Exception {
		try {
			this.restTemplate
					.getForObject("http://localhost:" + port() + "/welcome", String.class);
		} catch (Exception e) {
			// JSPs are not rendered
			then(e).hasMessage("404 Not Found");
		}

		then(ExceptionUtils.getLastException()).isNull();
		then(this.tracer.getCurrentSpan()).isNull();
	}

	private int port() {
		return this.environment.getProperty("local.server.port", Integer.class);
	}

}