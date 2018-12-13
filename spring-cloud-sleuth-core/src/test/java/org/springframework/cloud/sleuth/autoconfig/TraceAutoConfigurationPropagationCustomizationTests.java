package org.springframework.cloud.sleuth.autoconfig;

import brave.Tracing;
import brave.propagation.B3SinglePropagation;
import brave.propagation.ExtraFieldPropagation;
import org.assertj.core.api.BDDAssertions;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(
		classes = TraceAutoConfigurationPropagationCustomizationTests.Config.class,
		properties = "spring.sleuth.baggage-keys=my-baggage",
		webEnvironment = SpringBootTest.WebEnvironment.NONE
)
public class TraceAutoConfigurationPropagationCustomizationTests {

	@Autowired
	Tracing tracing;

	@Test
	public void usesCustomFactoryBuilder() {
		BDDAssertions.then(tracing.propagationFactory())
				.hasFieldOrPropertyWithValue("delegate", B3SinglePropagation.FACTORY);
	}

	@Configuration
	@EnableAutoConfiguration
	static class Config {

		@Bean
		public ExtraFieldPropagation.FactoryBuilder factoryBuilder() {
			return ExtraFieldPropagation.newFactoryBuilder(B3SinglePropagation.FACTORY);
		}

	}

}
