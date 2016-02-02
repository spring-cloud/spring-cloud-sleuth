/*
 * Copyright 2013-2015 the original author or authors.
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

package org.springframework.cloud.sleuth.log;

import java.lang.invoke.MethodHandles;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.sleuth.event.SpanReleasedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/**
 * @author Spencer Gibb
 */
public class JsonLogSpanListener {

	private static final Log log = LogFactory.getLog(MethodHandles.lookup().lookupClass());

	private final String prefix;
	private final String suffix;
	private final ObjectMapper objectMapper = new ObjectMapper();

	public JsonLogSpanListener() {
		this.prefix = "[span]";
		this.suffix = "[endspan]";
		this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
	}

	@EventListener(SpanReleasedEvent.class)
	@Order(Ordered.LOWEST_PRECEDENCE-10)
	public void stop(SpanReleasedEvent event) {
		try {
			log.info(this.prefix + this.objectMapper.writeValueAsString(event.getSpan()) +
					this.suffix);
		}
		catch (JsonProcessingException e) {
			log.error("Exception occurred while trying to parse JSON", e);
			throw new RuntimeException(e);
		}
	}

	public String getPrefix() {
		return this.prefix;
	}

	public String getSuffix() {
		return this.suffix;
	}

	public ObjectMapper getObjectMapper() {
		return this.objectMapper;
	}

	public boolean equals(Object o) {
		if (o == this)
			return true;
		if (!(o instanceof JsonLogSpanListener))
			return false;
		final JsonLogSpanListener other = (JsonLogSpanListener) o;
		if (!other.canEqual((Object) this))
			return false;
		final Object this$prefix = this.prefix;
		final Object other$prefix = other.prefix;
		if (this$prefix == null ?
				other$prefix != null :
				!this$prefix.equals(other$prefix))
			return false;
		final Object this$suffix = this.suffix;
		final Object other$suffix = other.suffix;
		if (this$suffix == null ?
				other$suffix != null :
				!this$suffix.equals(other$suffix))
			return false;
		final Object this$objectMapper = this.objectMapper;
		final Object other$objectMapper = other.objectMapper;
		if (this$objectMapper == null ?
				other$objectMapper != null :
				!this$objectMapper.equals(other$objectMapper))
			return false;
		return true;
	}

	public int hashCode() {
		final int PRIME = 59;
		int result = 1;
		final Object $prefix = this.prefix;
		result = result * PRIME + ($prefix == null ? 0 : $prefix.hashCode());
		final Object $suffix = this.suffix;
		result = result * PRIME + ($suffix == null ? 0 : $suffix.hashCode());
		final Object $objectMapper = this.objectMapper;
		result = result * PRIME + ($objectMapper == null ? 0 : $objectMapper.hashCode());
		return result;
	}

	protected boolean canEqual(Object other) {
		return other instanceof JsonLogSpanListener;
	}

	public String toString() {
		return "org.springframework.cloud.sleuth.log.JsonLogSpanListener(prefix="
				+ this.prefix + ", suffix=" + this.suffix + ", objectMapper="
				+ this.objectMapper + ")";
	}
}
