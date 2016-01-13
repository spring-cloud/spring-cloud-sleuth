package integration;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.cloud.sleuth.TraceManager;
import org.springframework.cloud.sleuth.trace.TraceContextHolder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
	@EnableAutoConfiguration(exclude = RabbitAutoConfiguration.class)
	@Slf4j
	public static class Config {

		@Bean SampleApp sampleApp() {
			return new SampleApp();
		}

	}
}
