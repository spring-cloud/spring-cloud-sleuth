/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.async.issues.issue1143;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.sleuth.DisableWebFluxSecurity;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static reactor.core.publisher.Mono.just;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT, classes = { TestApplication.class,
		Issue1143Tests.Config.class }, properties = "spring.main.web-application-type=reactive")
public class Issue1143Tests {

	@Autowired
	private WebTestClient webClient;

	@MockBean
	private TestRepository repository;

	@Captor
	private ArgumentCaptor<TestEntity> entityArgumentCaptor;

	@Test
	public void save_should_set_the_context() {
		// given
		TestEntity entity = new TestEntity("field1");
		given(this.repository.save(any())).willReturn(just(entity));

		// when
		this.webClient.post().uri("/test").contentType(APPLICATION_JSON)
				.accept(APPLICATION_JSON).header("X-Field2", "field2")
				.body(just(entity), TestEntity.class).exchange().expectStatus().isOk();

		// then
		verify(this.repository).save(this.entityArgumentCaptor.capture());
		TestEntity saved = this.entityArgumentCaptor.getValue();
		then(saved.getField2()).isEqualTo("field2");
	}

	@Configuration
	@DisableWebFluxSecurity
	static class Config {

	}

}
