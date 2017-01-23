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

package org.springframework.cloud.sleuth.instrument.web.client.feign.issues.issue393;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.netflix.feign.EnableFeignClients;
import org.springframework.cloud.netflix.feign.FeignClient;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.cloud.sleuth.trace.TestSpanContextHolder;
import org.springframework.cloud.sleuth.util.ExceptionUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author Marcin Grzejszczak
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = Application.class, webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
		properties = {"spring.application.name=demo-feign-uri",
		"server.port=9978", "eureka.client.enabled=true", "ribbon.eureka.enabled=true"})
public class Issue393Tests {

	RestTemplate template = new RestTemplate();
	@Autowired Tracer tracer;

	@Before
	public void open() {
		TestSpanContextHolder.removeCurrentSpan();
		ExceptionUtils.setFail(true);
	}

	@After
	public void close() {
		TestSpanContextHolder.removeCurrentSpan();
	}

	@Test
	public void should_successfully_work_when_service_discovery_is_on_classpath_and_feign_uses_url() {
		String url = "http://localhost:9978/hello/mikesarver";

		ResponseEntity<String> response = this.template.getForEntity(url, String.class);

		then(response.getBody()).isEqualTo("mikesarver foo");
		then(ExceptionUtils.getLastException()).isNull();
		then(this.tracer.getCurrentSpan()).isNull();
	}
}

@Configuration
@EnableAutoConfiguration
@EnableFeignClients
@EnableDiscoveryClient
class Application {

	@Bean
	public DemoController demoController(MyNameRemote myNameRemote) {
		return new DemoController(myNameRemote);
	}

	@Bean
	public feign.Logger.Level feignLoggerLevel() {
		return feign.Logger.Level.BASIC;
	}

	@Bean
	public AlwaysSampler defaultSampler() {
		return new AlwaysSampler();
	}

}

@FeignClient(name="no-name",
		url="http://localhost:9978")
interface MyNameRemote {

	@RequestMapping(value = "/name/{id}", method = RequestMethod.GET)
	String getName(@PathVariable("id") String id);
}

@RestController
class DemoController {

	private final MyNameRemote myNameRemote;

	public DemoController(MyNameRemote myNameRemote) {
		this.myNameRemote = myNameRemote;
	}

	@RequestMapping(value = "/hello/{name}")
	public String getHello(@PathVariable("name") String name) {
		return myNameRemote.getName(name) + " foo";
	}

	@RequestMapping(value = "/name/{name}")
	public String getName(@PathVariable("name") String name) {
		return name;
	}
}
