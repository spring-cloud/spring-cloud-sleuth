package integration;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.connection.RabbitConnectionFactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitProperties;
import org.springframework.cloud.sleuth.TraceManager;
import org.springframework.cloud.sleuth.trace.TraceContextHolder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.AbstractDockerIntegrationTest;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.Random;

/**
 * @author Marcin Grzejszczak
 */
@RestController
@Slf4j
public class SampleApp {

	@Autowired
	private TraceManager traceManager;

	@SneakyThrows
	@RequestMapping("/hi2")
	public String hi2() {
		log.info("I'm in the sample app");
		final Random random = new Random();
		int millis = random.nextInt(1000);
		Thread.sleep(millis);
		this.traceManager.addAnnotation("random-sleep-millis", String.valueOf(millis));
		log.info("Current span is [{}]", TraceContextHolder.getCurrentSpan());
		return "hi2";
	}

	@Configuration
	@EnableAutoConfiguration
	@Slf4j
	public static class Config {

		public static final int RABBITMQ_PORT = 5672;

		@Bean SampleApp sampleApp() {
			return new SampleApp();
		}

		@Bean
		@SneakyThrows
		ConnectionFactory connectionFactory() {
			RabbitProperties config = rabbitProperties();
			AbstractDockerIntegrationTest.await().until(() -> {
				try {
					ServerSocket serverSocket = new ServerSocket(RABBITMQ_PORT, 50,
							InetAddress.getByName(AbstractDockerIntegrationTest.getDockerURI().getHost()));
					serverSocket.close();
				} catch (IOException e) {
					log.info("RabbitMQ is up and running - proceeding");
					return true;
				}
				log.warn("RabbitMQ has not started yet...");
				return false;
			});
			RabbitConnectionFactoryBean factory = new RabbitConnectionFactoryBean();
			if (config.getHost() != null) {
				factory.setHost(config.getHost());
				factory.setPort(config.getPort());
			}
			if (config.getUsername() != null) {
				factory.setUsername(config.getUsername());
			}
			if (config.getPassword() != null) {
				factory.setPassword(config.getPassword());
			}
			if (config.getVirtualHost() != null) {
				factory.setVirtualHost(config.getVirtualHost());
			}
			if (config.getRequestedHeartbeat() != null) {
				factory.setRequestedHeartbeat(config.getRequestedHeartbeat());
			}
			RabbitProperties.Ssl ssl = config.getSsl();
			if (ssl.isEnabled()) {
				factory.setUseSSL(true);
				factory.setKeyStore(ssl.getKeyStore());
				factory.setKeyStorePassphrase(ssl.getKeyStorePassword());
				factory.setTrustStore(ssl.getTrustStore());
				factory.setTrustStorePassphrase(ssl.getTrustStorePassword());
			}
			factory.afterPropertiesSet();
			CachingConnectionFactory connectionFactory = new CachingConnectionFactory(
					factory.getObject());
			connectionFactory.setAddresses(config.getAddresses());
			return connectionFactory;
		}

		RabbitProperties rabbitProperties() {
			RabbitProperties rabbitProperties = new RabbitProperties();
			rabbitProperties.setHost(AbstractDockerIntegrationTest.getDockerURI().getHost());
			return rabbitProperties;
		}
	}
}
