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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HelloWorldImpl {
	
	private static final Logger LOG = LoggerFactory.getLogger(HelloWorldImpl.class);

	public String invokeProcessor(String message) throws InterruptedException {
		LOG.info(" input message "+message);
		Thread.currentThread().sleep(500);
		LOG.info(" After the Sleep "+message);
		String responseMessage = message + " Persist into DB ";
		return responseMessage;
	}
	
	
	public List<String> aggregate(List<String> requestMessage) {
		LOG.info(Thread.currentThread().getName());
		LOG.info(" requestMessage aggregate "+requestMessage);
		return requestMessage;
	}
	
	
	public List<String> splitMessage(String[] splitRequest){
		LOG.info(" Inside splitMessage " +splitRequest);
		List<String> splitGBSResponse = new ArrayList<String>();
		splitGBSResponse = Arrays.asList(splitRequest);
		return splitGBSResponse;
	}
}
