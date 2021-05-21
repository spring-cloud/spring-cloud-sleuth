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

package org.springframework.cloud.sleuth.instrument.jdbc;

import java.util.Collections;

import com.p6spy.engine.event.CompoundJdbcEventListener;
import com.p6spy.engine.event.SimpleJdbcEventListener;
import com.p6spy.engine.spy.JdbcEventListenerFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

class ContextJdbcEventListenerFactoryTest {

	private JdbcEventListenerFactory delegate;

	@BeforeEach
	void setUp() {
		delegate = Mockito.mock(JdbcEventListenerFactory.class);
	}

	@Test
	void shouldUseDelegateToCreateListener() {
		SimpleJdbcEventListener listener1 = new SimpleJdbcEventListener() {
		};
		SimpleJdbcEventListener listener2 = new SimpleJdbcEventListener() {
		};
		Mockito.when(delegate.createJdbcEventListener()).thenReturn(listener1);
		TraceP6SpyContextJdbcEventListenerFactory contextJdbcEventListenerFactory = new TraceP6SpyContextJdbcEventListenerFactory(
				delegate, Collections.singletonList(listener2));

		CompoundJdbcEventListener jdbcEventListener = (CompoundJdbcEventListener) contextJdbcEventListenerFactory
				.createJdbcEventListener();
		assertThat(jdbcEventListener.getEventListeners()).hasSize(2);
		assertThat(jdbcEventListener.getEventListeners()).contains(listener1, listener2);
	}

	@Test
	void shouldReuseCompoundListenerFromFactory() {
		SimpleJdbcEventListener listener1 = new SimpleJdbcEventListener() {
		};
		Mockito.when(delegate.createJdbcEventListener()).thenReturn(new CompoundJdbcEventListener());
		TraceP6SpyContextJdbcEventListenerFactory contextJdbcEventListenerFactory = new TraceP6SpyContextJdbcEventListenerFactory(
				delegate, Collections.singletonList(listener1));

		CompoundJdbcEventListener jdbcEventListener = (CompoundJdbcEventListener) contextJdbcEventListenerFactory
				.createJdbcEventListener();
		assertThat(jdbcEventListener.getEventListeners()).hasSize(1);
		assertThat(jdbcEventListener.getEventListeners()).contains(listener1);
	}

}
