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

package org.springframework.cloud.sleuth.autoconfig.brave.instrument.mongodb;

import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.sleuth.autoconfig.brave.BraveAutoConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Chintan Radia
 */
class BraveMongoDbAutoConfigurationAsyncDriverTest {

	@Test
	void should_not_auto_configure_brave_mongo_db() {
		new ApplicationContextRunner()
				.withConfiguration(
						AutoConfigurations.of(BraveAutoConfiguration.class, BraveMongoDbAutoConfiguration.class))
				.run(context -> assertThat(context).doesNotHaveBean(BraveMongoDbAutoConfiguration.class));
	}

	@Test
	void should_auto_configure_brave_mongo_db() {
		new ApplicationContextRunner()
				.withClassLoader(new FilteredClassLoader("com.mongodb.reactivestreams.client.MongoClient"))
				.withConfiguration(
						AutoConfigurations.of(BraveAutoConfiguration.class, BraveMongoDbAutoConfiguration.class))
				.run(context -> assertThat(context).hasSingleBean(BraveMongoDbAutoConfiguration.class));
	}

}
