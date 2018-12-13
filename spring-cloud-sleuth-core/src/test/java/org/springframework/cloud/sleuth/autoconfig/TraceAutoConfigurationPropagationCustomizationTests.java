package org.springframework.cloud.sleuth.autoconfig;

import brave.propagation.B3Propagation;
import brave.propagation.B3SinglePropagation;
import brave.propagation.ExtraFieldPropagation;
import brave.propagation.Propagation;
import org.assertj.core.api.BDDAssertions;
import org.junit.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

public class TraceAutoConfigurationPropagationCustomizationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(TraceAutoConfiguration.class));

	@Test
	public void stillCreatesDefault() {
		this.contextRunner.run((context) -> {
					BDDAssertions.then(context.getBean(Propagation.Factory.class))
							.isEqualTo(B3Propagation.FACTORY);
				});
	}

	@Test
	public void allowsCustomization() {
		this.contextRunner
				.withPropertyValues("spring.sleuth.baggage-keys=my-baggage")
				.run((context) -> {
					BDDAssertions.then(context.getBean(Propagation.Factory.class))
							.hasFieldOrPropertyWithValue("delegate", B3Propagation.FACTORY);
				});
	}

	@Test
	public void allowsCustomizationOfBuilder() {
		this.contextRunner
				.withPropertyValues("spring.sleuth.baggage-keys=my-baggage")
				.withUserConfiguration(Config.class)
				.run((context) -> {
					BDDAssertions.then(context.getBean(Propagation.Factory.class))
							.hasFieldOrPropertyWithValue("delegate", B3SinglePropagation.FACTORY);
		});
	}

	@Configuration
	static class Config {

		@Bean
		public ExtraFieldPropagation.FactoryBuilder factoryBuilder() {
			return ExtraFieldPropagation.newFactoryBuilder(B3SinglePropagation.FACTORY);
		}

	}

}
