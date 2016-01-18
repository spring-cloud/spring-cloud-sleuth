/*
 * Copyright 2016 the original author or authors.
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
package org.springframework.cloud.sleuth.zipkin.stream;

import io.zipkin.Sampler;
import org.junit.Test;
import org.springframework.cloud.sleuth.MilliSpan;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.stream.Host;
import org.springframework.cloud.sleuth.stream.Spans;

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class SamplingZipkinSpanIteratorTests {

  Host host = new Host("myservice", "1.2.3.4", 8080);

  @Test
  public void skipsInputSpans() {
    Spans spans = new Spans(host, Arrays.asList(span("message/sleuth")));

    Iterator<io.zipkin.Span> result = new SamplingZipkinSpanIterator(Sampler.create(1.0f), spans);

    assertThat(result).isEmpty();
  }

  @Test
  public void retainsValidSpans() {
    Spans spans = new Spans(host, Arrays.asList(span("foo"), span("bar"), span("baz")));

    Iterator<io.zipkin.Span> result = new SamplingZipkinSpanIterator(Sampler.create(1.0f), spans);

    assertThat(result).extracting(s -> s.name).containsExactly("foo", "bar", "baz");
  }

  @Test
  public void retainsOnlySampledSpans() {
    Spans spans = new Spans(host, Arrays.asList(span("foo"), span("bar"), span("baz")));

    Sampler everyOtherSampler = new Sampler() {
      AtomicInteger counter = new AtomicInteger();

      public boolean isSampled(long l) {
        return counter.getAndIncrement() % 2 == 0;
      }
    };

    Iterator<io.zipkin.Span> result = new SamplingZipkinSpanIterator(everyOtherSampler, spans);

    assertThat(result).extracting(s -> s.name).containsExactly("foo", "baz");
  }

  Span span(String name) {
    Long id = new Random().nextLong();
    return new MilliSpan(1, 3, name, id, Collections.<Long>emptyList(), id, true, true, "process");
  }
}