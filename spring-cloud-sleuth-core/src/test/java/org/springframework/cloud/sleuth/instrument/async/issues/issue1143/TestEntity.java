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

import brave.propagation.ExtraFieldPropagation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestEntity {

	private static final Logger log = LoggerFactory.getLogger(TestEntity.class);

	private String field1;

	private String field2;

	public TestEntity() {
		super();
	}

	TestEntity(String field1) {
		this.field1 = field1;
	}

	public String getField1() {
		return this.field1;
	}

	public String getField2() {
		return this.field2;
	}

	TestEntity setContext() {
		log.info("BAR 1");
		this.field2 = ExtraFieldPropagation.get("X-Field2");
		log.info("BAR 2 [" + this.field2 + "]");
		return this;
	}

}
