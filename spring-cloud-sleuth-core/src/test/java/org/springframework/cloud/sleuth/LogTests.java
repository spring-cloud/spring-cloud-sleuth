/*
 * Copyright 2013-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.springframework.cloud.sleuth;

import java.io.IOException;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author Adrian Cole
 */
public class LogTests {

	@Rule
	public ExpectedException thrown = ExpectedException.none();

	ObjectMapper objectMapper = new ObjectMapper();

	@Test public void ctor_missing_event() throws IOException {
		this.thrown.expect(NullPointerException.class);
		this.thrown.expectMessage("event");

		new Log(1234L, null);
	}

	@Test public void serialization_round_trip() throws IOException {
		Log log = new Log(1234L, "cs");

		String serialized = this.objectMapper.writeValueAsString(log);
		Log deserialized = this.objectMapper.readValue(serialized, Log.class);

		then(deserialized).isEqualTo(log);
	}

	@Test public void deserialize_missing_event() throws IOException {
		this.thrown.expect(JsonMappingException.class);

		this.objectMapper.readValue("{\"timestamp\": 1234}", Log.class);
	}
}
