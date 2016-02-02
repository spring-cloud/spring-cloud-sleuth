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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.apachecommons.CommonsLog;
import org.springframework.cloud.sleuth.event.SpanReleasedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/**
 * @author Spencer Gibb
 */
@CommonsLog
@Data
public class JsonLogSpanListener {

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

}
