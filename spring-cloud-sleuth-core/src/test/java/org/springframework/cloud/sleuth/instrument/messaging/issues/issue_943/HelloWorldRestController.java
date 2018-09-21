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

package org.springframework.cloud.sleuth.instrument.messaging.issues.issue_943;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.PollableChannel;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloWorldRestController {

	private static final Logger LOG = LoggerFactory.getLogger(HelloWorldRestController.class);

	@Autowired
	private ApplicationContext applicationContext;

	@RequestMapping(path = "getHelloWorldMessage", method = RequestMethod.GET, produces = MediaType.TEXT_PLAIN_VALUE)
	public ResponseEntity<String> getHelloWorld() throws Exception {
		
		LOG.info("Inside getHelloWorldMessage");

		String[] requestMessage = new String[3];
		requestMessage[0] = "Hellow World Message 1";
		requestMessage[1] = "Hellow World Message 2";
		requestMessage[2] = "Hellow World Message 3";

		PollableChannel outputChannel = (PollableChannel) applicationContext.getBean("messagingOutputChannel");
		
		MessagingGateway messagingGateway = (MessagingGateway) applicationContext
				.getBean("messagingGateway");
		
		messagingGateway.processMessage(requestMessage);

		GenericMessage reply = (GenericMessage) outputChannel.receive();

		List<String> body = (List<String>) reply.getPayload();

		LOG.info(" Response Message " + body);

		return new ResponseEntity<String>(body.toString(), HttpStatus.OK);
	}

}
