/*
 * Copyright 2013-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package sample;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

@Controller
public class GreetingController {

	private static final Log log = LogFactory.getLog(GreetingController.class);

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
