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

import java.util.List;

import com.p6spy.engine.event.CompoundJdbcEventListener;
import com.p6spy.engine.event.JdbcEventListener;
import com.p6spy.engine.spy.JdbcEventListenerFactory;

import org.springframework.util.Assert;

/**
 * Factory to support all defined {@link JdbcEventListener} in the context.
 *
 * @author Arthur Gavlyukovskiy
 * @since 3.1.0
 */
public class TraceP6SpyContextJdbcEventListenerFactory implements JdbcEventListenerFactory {

	private final CompoundJdbcEventListener compoundJdbcEventListener;

	public TraceP6SpyContextJdbcEventListenerFactory(JdbcEventListenerFactory delegate,
			List<JdbcEventListener> listeners) {
		Assert.notNull(delegate, "JdbcEventListenerFactory should not be null");
		Assert.notEmpty(listeners, "Listeners should not be empty");

		JdbcEventListener jdbcEventListener = delegate.createJdbcEventListener();
		if (jdbcEventListener instanceof CompoundJdbcEventListener) {
			this.compoundJdbcEventListener = (CompoundJdbcEventListener) jdbcEventListener;
		}
		else {
			this.compoundJdbcEventListener = new CompoundJdbcEventListener();
			this.compoundJdbcEventListener.addListener(jdbcEventListener);
		}
		listeners.forEach(this.compoundJdbcEventListener::addListener);
	}

	@Override
	public JdbcEventListener createJdbcEventListener() {
		return this.compoundJdbcEventListener;
	}

}
