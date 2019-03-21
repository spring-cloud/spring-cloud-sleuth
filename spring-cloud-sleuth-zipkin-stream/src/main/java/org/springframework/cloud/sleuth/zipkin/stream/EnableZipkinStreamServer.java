/**
 * Copyright 2015 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.springframework.cloud.sleuth.zipkin.stream;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.cloud.sleuth.stream.SleuthSink;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.context.annotation.Import;
import zipkin.server.EnableZipkinServer;

/**
 * When enabled, instrumented apps will transport spans over a
 * Spring Cloud Stream, for example RabbitMQ.
 *
 * @author Dave Syer
 * @since 1.0.0
 *
 * @see ZipkinMessageListener
 * @deprecated Please switch to the normal Zipkin server which supports RabbitMQ and Kafka.
 * See <a href="https://cloud.spring.io/spring-cloud-sleuth/single/spring-cloud-sleuth.html#_sleuth_with_zipkin_over_rabbitmq_or_kafka">our documentation</a> for more.
 */
@Deprecated
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@EnableBinding(SleuthSink.class)
@EnableZipkinServer
@Import(ZipkinMessageListener.class)
public @interface EnableZipkinStreamServer {

}
