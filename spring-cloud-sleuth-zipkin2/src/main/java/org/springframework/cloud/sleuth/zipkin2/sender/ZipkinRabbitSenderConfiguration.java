package org.springframework.cloud.sleuth.zipkin2.sender;

import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.amqp.RabbitProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import zipkin2.reporter.Sender;
import zipkin2.reporter.amqp.RabbitMQSender;

@Configuration
@ConditionalOnBean(CachingConnectionFactory.class)
@ConditionalOnMissingBean(Sender.class)
@Conditional(ZipkinSenderCondition.class)
class ZipkinRabbitSenderConfiguration {
	@Value("${spring.zipkin.rabbitmq.queue:zipkin}")
	private String queue;

	@Bean Sender rabbitSender(CachingConnectionFactory connectionFactory, RabbitProperties config) {
		return RabbitMQSender.newBuilder()
				.connectionFactory(connectionFactory.getRabbitConnectionFactory())
				.queue(this.queue)
				.addresses(config.determineAddresses())
				.build();
	}
}
