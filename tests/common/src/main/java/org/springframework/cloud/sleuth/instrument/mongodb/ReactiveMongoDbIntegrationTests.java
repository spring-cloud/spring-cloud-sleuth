/*
 * Copyright 2013-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.sleuth.instrument.mongodb;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.TraceContext;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.exporter.FinishedSpan;
import org.springframework.cloud.sleuth.test.TestSpanHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.BDDAssertions.then;

@ContextConfiguration(classes = ReactiveMongoDbIntegrationTests.TestConfig.class)
@Testcontainers
@Tag("DockerRequired")
public abstract class ReactiveMongoDbIntegrationTests {

	private static final Log log = LogFactory.getLog(ReactiveMongoDbIntegrationTests.class);

	@Autowired
	TestSpanHandler spans;

	@Autowired
	Tracer tracer;

	@Autowired
	BasicUserRepository basicUserRepository;

	@Container
	static MongoDBContainer mongoDBContainer = new MongoDBContainer(DockerImageName.parse("mongo").withTag("4.4.7"));

	@DynamicPropertySource
	static void setProperties(DynamicPropertyRegistry registry) {
		mongoDBContainer.start();
		registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
	}

	@BeforeEach
	void setup() {
		this.spans.clear();
	}

	@Test
	public void should_pass_tracing_information_when_using_reactive_mongodb() {
		// given
		Span nextSpan = this.tracer.nextSpan().name("mongo-reactive-app");

		// when
		Mono.just(nextSpan).doOnNext(span -> this.tracer.withSpan(nextSpan.start())).flatMap(span -> {
			log.info("Hello from flat map");
			return this.basicUserRepository.save(new User("foo", "bar", "baz", null))
					.flatMap(user -> this.basicUserRepository.findUserByUsername("foo"));
		}).contextWrite(context -> context.put(Span.class, nextSpan).put(TraceContext.class, nextSpan.context()))
				.doFinally(signalType -> nextSpan.end()).block(Duration.ofMinutes(1));

		// then
		List<FinishedSpan> reportedSpans = this.spans.reportedSpans();
		then(reportedSpans.stream().map(FinishedSpan::getTraceId).collect(Collectors.toSet()))
				.as("There must be only 1 trace id").hasSize(1);
		List<String> mongoSpanNames = reportedSpans.stream()
				.filter(fs -> fs.getName().equals("insert user") || fs.getName().equals("find user"))
				.map(FinishedSpan::getName).collect(Collectors.toList());
		then(mongoSpanNames).as("There must be first an insert then a find").containsExactly("insert user",
				"find user");
	}

	@Configuration(proxyBeanMethods = false)
	@EnableAutoConfiguration
	public static class TestConfig {

	}

}

interface BasicUserRepository extends ReactiveCrudRepository<User, Long> {

	Mono<User> findUserByUsername(String username);

}

class User {

	private String id;

	private String username;

	private String firstname;

	private String lastname;

	User() {
	}

	User(String id) {
		this.setId(id);
	}

	User(String username, String firstname, String lastname, String id) {
		this.username = username;
		this.firstname = firstname;
		this.lastname = lastname;
		this.id = id;
	}

	String getId() {
		return this.id;
	}

	void setId(String id) {
		this.id = id;
	}

	String getUsername() {
		return this.username;
	}

	void setUsername(String username) {
		this.username = username;
	}

	String getFirstname() {
		return this.firstname;
	}

	void setFirstname(String firstname) {
		this.firstname = firstname;
	}

	String getLastname() {
		return this.lastname;
	}

	void setLastname(String lastname) {
		this.lastname = lastname;
	}

}
