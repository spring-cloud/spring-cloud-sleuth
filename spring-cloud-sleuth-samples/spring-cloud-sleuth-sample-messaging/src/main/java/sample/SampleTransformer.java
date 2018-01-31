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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;

/**
 * @author Dave Syer
 *
 */
@MessageEndpoint
public class SampleTransformer {

	private static final Log log = LogFactory.getLog(SampleTransformer.class);

	@Autowired
	SampleBackground background;

	@ServiceActivator(inputChannel="xform")
	public String log(Message<?> message) throws InterruptedException {
		log.info("Received: " + message);
		this.background.background();
		return message.getPayload().toString().toUpperCase();
	}

}
