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

package sample;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(classes = SampleFeignApplication.class)
@TestPropertySource(properties = "sample.zipkin.enabled=false")
@ExtendWith(OutputCaptureExtension.class)
public class SampleFeignApplicationTests {

	private static final Log log = LogFactory.getLog(SampleFeignApplicationTests.class);

	@Test
	public void contextLoads() {
	}

	// https://github.com/spring-cloud/spring-cloud-sleuth/issues/1396
	@Test
	public void should_not_pass_dash_as_default_service_name(CapturedOutput outputCapture) {
		log.info("HELLO");

		BDDAssertions.then(outputCapture.toString()).doesNotContain("INFO [-,,,]");
	}

}
