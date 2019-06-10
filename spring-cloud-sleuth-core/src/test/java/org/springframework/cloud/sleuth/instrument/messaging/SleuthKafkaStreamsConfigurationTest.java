package org.springframework.cloud.sleuth.instrument.messaging;

import java.util.HashMap;

import brave.Tracing;
import org.apache.kafka.streams.KafkaClientSupplier;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.KafkaStreamsConfiguration;
import org.springframework.kafka.config.StreamsBuilderFactoryBean;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = SleuthKafkaStreamsConfigurationTest.Config.class, webEnvironment = WebEnvironment.NONE)
public class SleuthKafkaStreamsConfigurationTest {

	@Autowired
	TestTraceStreamsBuilderFactoryBean bean;

	@Test
	public void clientSupplierInvokedOnStreamsBuilderFactoryBean() {
		Assert.assertTrue("StreamsBuilderFactoryBean#setClientSupplier(KafkaClientSupplier) not called",
				bean.isClientSupplierInvoked());
	}

	@Configuration
	@EnableAutoConfiguration
	protected static class Config {
		@Bean
		Tracing tracing() {
			return Tracing.newBuilder().build();
		}

		@Bean
		KafkaStreamsConfiguration kafkaStreamsConfiguration() {
			return new KafkaStreamsConfiguration(new HashMap<>());
		}

		@Bean
		StreamsBuilderFactoryBean streamsBuilderFactoryBean() {
			TestTraceStreamsBuilderFactoryBean factoryBean = new TestTraceStreamsBuilderFactoryBean();
			factoryBean.setAutoStartup(false);
			return factoryBean;
		}

	}
}

class TestTraceStreamsBuilderFactoryBean extends StreamsBuilderFactoryBean {
	private boolean clientSupplierInvoked;

	@Override
	public void setClientSupplier(KafkaClientSupplier clientSupplier) {
		this.clientSupplierInvoked = true;
		super.setClientSupplier(clientSupplier);
	}

	public boolean isClientSupplierInvoked() {
		return clientSupplierInvoked;
	}
}
