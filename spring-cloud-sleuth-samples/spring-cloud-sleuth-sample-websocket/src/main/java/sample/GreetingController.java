package sample;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

@Controller
public class GreetingController {

	private static Logger log = LoggerFactory.getLogger(GreetingController.class);

	@MessageMapping("/hello")
	@SendTo("/topic/greetings")
	public Greeting greeting(HelloMessage message) throws Exception {
		log.info("Hello: " + message);
		Thread.sleep(3000); // simulated delay
		// Then send back greeting
		return new Greeting("Hello, " + message.getName() + "!");
	}

}

class Greeting {

	private String content;

	public Greeting(String content) {
		this.content = content;
	}

	public String getContent() {
		return this.content;
	}

}

class HelloMessage {

	private String name;

	public String getName() {
		return this.name;
	}

}
