package org.springframework.cloud.sleuth.instrument.web.multiple;

import java.util.Arrays;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.integration.annotation.Aggregator;
import org.springframework.integration.annotation.Gateway;
import org.springframework.integration.annotation.IntegrationComponentScan;
import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.MessagingGateway;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.annotation.Splitter;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@MessageEndpoint
@IntegrationComponentScan
public class DemoApplication {

	private static final Log log = LogFactory.getLog(DemoApplication.class);

	@Autowired
	Sender sender;

	@RequestMapping("/greeting")
	public Greeting greeting(@RequestParam(defaultValue="Hello World!") String message) {
		this.sender.send(message);
		return new Greeting(message);
	}

	@Splitter(inputChannel="greetings", outputChannel="words")
	public List<String> words(String greeting) {
		return Arrays.asList(StringUtils.delimitedListToStringArray(greeting, " "));
	}

	@Aggregator(inputChannel="words", outputChannel="counts")
	public int count(List<String> greeting) {
		return greeting.size();
	}

	@ServiceActivator(inputChannel="counts")
	public void report(int count) {
		log.info("Count: " + count);
	}

}

@MessagingGateway(name = "greeter")
interface Sender {
	@Gateway(requestChannel = "greetings")
	void send(String message);
}

class Greeting {
	private String message;

	Greeting() {
	}

	public Greeting(String message) {
		super();
		this.message = message;
	}

	public String getMessage() {
		return this.message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

}