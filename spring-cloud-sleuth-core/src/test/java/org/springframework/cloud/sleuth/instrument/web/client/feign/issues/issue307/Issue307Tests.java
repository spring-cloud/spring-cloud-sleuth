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

package org.springframework.cloud.sleuth.instrument.web.client.feign.issues.issue307;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.cloud.client.circuitbreaker.EnableCircuitBreaker;
import org.springframework.cloud.netflix.feign.EnableFeignClients;
import org.springframework.cloud.netflix.feign.FeignClient;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.cloud.sleuth.trace.TestSpanContextHolder;
import org.springframework.cloud.sleuth.util.ExceptionUtils;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;

import static org.assertj.core.api.BDDAssertions.then;

public class Issue307Tests {

	@Before
	public void setup() {
		TestSpanContextHolder.removeCurrentSpan();
	}

	@Test
	public void should_start_context() {
		try (ConfigurableApplicationContext applicationContext = SpringApplication
				.run(SleuthSampleApplication.class, "--spring.jmx.enabled=false", "--server.port=0")) {
		}
		then(ExceptionUtils.getLastException()).isNull();
	}
}

@EnableAutoConfiguration
@Import({ParticipantsBean.class, ParticipantsClient.class})
@RestController
@EnableFeignClients
@EnableCircuitBreaker
class SleuthSampleApplication {

	private static final Logger LOG = Logger.getLogger(SleuthSampleApplication.class.getName());

	@Autowired
	private RestTemplate restTemplate;

	@Autowired
	private Environment environment;

	@Autowired
	private ParticipantsBean participantsBean;

	@Bean
	public RestTemplate getRestTemplate() {
		return new RestTemplate();
	}

	@Bean
	public AlwaysSampler defaultSampler() {
		return new AlwaysSampler();
	}

	@RequestMapping("/")
	public String home() {
		LOG.log(Level.INFO, "you called home");
		return "Hello World";
	}

	@RequestMapping("/callhome")
	public String callHome() {
		LOG.log(Level.INFO, "calling home");
		return restTemplate.getForObject("http://localhost:" + port(), String.class);
	}

	private int port() {
		return this.environment.getProperty("local.server.port", Integer.class);
	}
}

@Component
class ParticipantsBean {
	@Autowired
	private ParticipantsClient participantsClient;

	@HystrixCommand(fallbackMethod = "defaultParticipants")
	public List<Object> getParticipants(String raceId) {
		return participantsClient.getParticipants(raceId);
	}

	public List<Object> defaultParticipants(String raceId) {
		return new ArrayList<>();
	}
}

@FeignClient("participants")
interface ParticipantsClient {

	@RequestMapping(method = RequestMethod.GET, value="/races/{raceId}")
	List<Object> getParticipants(@PathVariable("raceId") String raceId);

}
