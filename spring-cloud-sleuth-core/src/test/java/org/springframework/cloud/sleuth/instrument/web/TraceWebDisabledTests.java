package org.springframework.cloud.sleuth.instrument.web;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * @author Marcin Grzejszczak
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = { TraceWebDisabledTests.Config.class },
		properties = { "spring.sleuth.web.enabled=true",  "spring.sleuth.web.client.enabled=false"})
public class TraceWebDisabledTests {

	@Test
	public void should_load_context() {

	}

	@Configuration
	@EnableAutoConfiguration
	public static class Config {}
}
