/*
 * Copyright 2013-2020 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.kafka;

import java.util.Map;
import java.util.Optional;

import org.apache.kafka.clients.producer.ProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.MDC;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.TraceContext;
import org.springframework.cloud.sleuth.Tracer;

public class TraceProducerInterceptor implements ProducerInterceptor {

	private Tracer tracer;

	private Span span;

	@Override
	public ProducerRecord onSend(ProducerRecord producerRecord) {
		// System.out.println(
		// Thread.currentThread().getName() + " traceId " + MDC.get("traceId") + " ,
		// spanId " + MDC.get("spanId"));
		Span.Builder spanBuilder = tracer.spanBuilder().kind(Span.Kind.PRODUCER).name("kafka.produce")
				.tag("kafka.topic", producerRecord.topic());
		this.span = spanBuilder.start();
		String spanId = Optional.ofNullable(tracer.currentSpan()).map(Span::context).map(TraceContext::spanId)
				.orElse(null);
		System.out.println(Thread.currentThread().getName() + " " + spanId + " traceId " + MDC.get("traceId")
				+ " , spanId " + MDC.get("spanId"));
		return producerRecord;
	}

	@Override
	public void onAcknowledgement(RecordMetadata recordMetadata, Exception e) {
		String spanId = Optional.ofNullable(tracer.currentSpan()).map(Span::context).map(TraceContext::spanId)
				.orElse(null);
		System.out.println(Thread.currentThread().getName() + " " + spanId + " traceId " + MDC.get("traceId")
				+ " , spanId " + MDC.get("spanId"));
		System.out.println(this.span.context().spanId());
		this.span.end();
		// System.out.println(
		// Thread.currentThread().getName() + " traceId " + MDC.get("traceId") + " ,
		// spanId " + MDC.get("spanId"));
	}

	@Override
	public void close() {

	}

	@Override
	public void configure(Map<String, ?> map) {
		this.tracer = (Tracer) map.get("tracer");
	}

}
