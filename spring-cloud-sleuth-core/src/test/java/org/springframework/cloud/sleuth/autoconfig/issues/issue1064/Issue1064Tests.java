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

package org.springframework.cloud.sleuth.autoconfig.issues.issue1064;

import org.assertj.core.api.BDDAssertions;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.cloud.stream.messaging.Processor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;

/**
 * @author Marcin Grzejszczak
 */
public class Issue1064Tests {
	@Autowired Gitter3813xApplication application;

	@Test
	public void should_store_the_headers() {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
		context.register(Gitter3813xApplication.class);

		context.refresh();

		Gitter3813xApplication application = context.getBean(Gitter3813xApplication.class);
		BDDAssertions.then(application.getIn()).isNotNull();
		BDDAssertions.then(application.getIn().getHeaders()).containsEntry("foo", "bar");
	}
}

@SpringBootApplication
@EnableBinding(Processor.class)
class Gitter3813xApplication {

	private Message<?> in;

	@Bean
	public ApplicationRunner runner(MessageChannel output, Environment environment) {
		return args -> output.send(
				MessageBuilder.withPayload("foo").setHeader("foo", "bar").build());
	}

	@StreamListener(Processor.INPUT)
	public void listen(Message<?> in) {
		this.in = in;
	}

	public Message<?> getIn() {
		return this.in;
	}
}