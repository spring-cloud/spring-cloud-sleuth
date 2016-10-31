package org.springframework.cloud.sleuth.instrument.web;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.boot.test.WebIntegrationTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * @author Marcin Grzejszczak
 */
@RunWith(SpringJUnit4ClassRunner.class)
@WebIntegrationTest({ "server.port=0", "spring.sleuth.web.enabled=true",  "spring.sleuth.web.client.enabled=false"})
@SpringApplicationConfiguration(classes = { TraceWebDisabledTests.Config.class })
public class TraceWebDisabledTests {

	@Test
	public void should_load_context() {

	}

	@Configuration
	@EnableAutoConfiguration
	public static class Config {}
}
