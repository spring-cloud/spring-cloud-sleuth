/*
 * Copyright 2013-2017 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.web;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.sleuth.Sampler;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.assertions.ListOfSpans;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.cloud.sleuth.trace.TestSpanContextHolder;
import org.springframework.cloud.sleuth.util.ArrayListSpanAccumulator;
import org.springframework.cloud.sleuth.util.ExceptionUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.hateoas.PagedResources;
import org.springframework.http.RequestEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.client.RestTemplate;

import org.awaitility.Awaitility;
import javax.annotation.PostConstruct;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import java.net.URI;
import java.util.stream.Stream;

import static org.assertj.core.api.BDDAssertions.then;
import static org.springframework.cloud.sleuth.assertions.SleuthAssertions.then;

/**
 * @author Marcin Grzejszczak
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = ReservationServiceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext
@ActiveProfiles("data")
public class SpringDataInstrumentationTests {

	@Autowired
	RestTemplate restTemplate;
	@Autowired
	Environment environment;
	@Autowired
	Tracer tracer;
	@Autowired
	ArrayListSpanAccumulator arrayListSpanAccumulator;

	@Before
	public void setup() {
		TestSpanContextHolder.removeCurrentSpan();
	}

	@Test
	public void should_create_span_instrumented_by_a_handler_interceptor() {
		long noOfNames = namesCount();

		then(noOfNames).isEqualTo(8);
		then(this.arrayListSpanAccumulator.getSpans()).isNotEmpty();
		Awaitility.await().untilAsserted(() -> {
			then(new ListOfSpans(this.arrayListSpanAccumulator.getSpans()))
					.hasASpanWithName("http:/reservations")
					.hasASpanWithTagKeyEqualTo("mvc.controller.class");
		});
		then(this.tracer.getCurrentSpan()).isNull();
		then(ExceptionUtils.getLastException()).isNull();
		then(new ListOfSpans(this.arrayListSpanAccumulator.getSpans())).hasRpcLogsInProperOrder();
	}

	long namesCount() {
		return
				this.restTemplate.exchange(RequestEntity
						.get(URI.create("http://localhost:" + port() + "/reservations")).build(), PagedResources.class)
				.getBody().getMetadata().getTotalElements();
	}

	private int port() {
		return this.environment.getProperty("local.server.port", Integer.class);
	}
}

@Configuration
@EnableAutoConfiguration
@EntityScan(basePackageClasses = Reservation.class)
class ReservationServiceApplication {

	@Bean
	RestTemplate restTemplate() {
		return new RestTemplate();
	}

	@Bean
	SampleRecords sampleRecords(ReservationRepository reservationRepository) {
		return new SampleRecords(reservationRepository);
	}

	@Bean
	ArrayListSpanAccumulator arrayListSpanAccumulator() {
		return new ArrayListSpanAccumulator();
	}

	@Bean
	Sampler alwaysSampler() {
		return new AlwaysSampler();
	}

}

class SampleRecords {

	private final ReservationRepository reservationRepository;

	public SampleRecords(ReservationRepository reservationRepository) {
		this.reservationRepository = reservationRepository;
	}

	@PostConstruct
	public void create() throws Exception {
		Stream.of("Josh", "Jungryeol", "Nosung", "Hyobeom", "Soeun", "Seunghue", "Peter",
				"Jooyong")
				.forEach(name -> reservationRepository.save(new Reservation(name)));
		reservationRepository.findAll().forEach(System.out::println);
	}
}

@RepositoryRestResource
interface ReservationRepository extends JpaRepository<Reservation, Long> {
}

@Entity
class Reservation {

	@Id
	@GeneratedValue
	private Long id; // id

	private String reservationName; // reservation_name

	public Long getId() {
		return id;
	}

	public String getReservationName() {
		return reservationName;
	}

	@Override
	public String toString() {
		return "Reservation{" + "id=" + id + ", reservationName='" + reservationName
				+ '\'' + '}';
	}

	Reservation() {// why JPA why???
	}

	public Reservation(String reservationName) {

		this.reservationName = reservationName;
	}
}
