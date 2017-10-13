package org.springframework.cloud.sleuth.zipkin2.sender;

import java.util.List;
import java.util.Map;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import zipkin2.reporter.Sender;
import zipkin2.reporter.kafka11.KafkaSender;

@Configuration
@ConditionalOnClass(ByteArraySerializer.class)
@ConditionalOnBean(KafkaProperties.class)
@ConditionalOnMissingBean(Sender.class)
@Conditional(ZipkinSenderCondition.class)
class ZipkinKafkaSenderConfiguration {
	@Value("${spring.zipkin.kafka.topic:zipkin}")
	private String topic;

	@Bean Sender kafkaSender(KafkaProperties config) {
		Map<String, Object> properties = config.buildProducerProperties();
		properties.put("key.serializer", ByteArraySerializer.class.getName());
		properties.put("value.serializer", ByteArraySerializer.class.getName());
		// Kafka expects the input to be a String, but KafkaProperties returns a list
		Object bootstrapServers = properties.get("bootstrap.servers");
		if (bootstrapServers instanceof List) {
			properties.put("bootstrap.servers", join((List) bootstrapServers));
		}
		return KafkaSender.newBuilder()
				.topic(this.topic)
				.overrides(properties)
				.build();
	}

	static String join(List<?> parts) {
		StringBuilder to = new StringBuilder();
		for (int i = 0, length = parts.size(); i < length; i++) {
			to.append(parts.get(i));
			if (i + 1 < length) {
				to.append(',');
			}
		}
		return to.toString();
	}
}
