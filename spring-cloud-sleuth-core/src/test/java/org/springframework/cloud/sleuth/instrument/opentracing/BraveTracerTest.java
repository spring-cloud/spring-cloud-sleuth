/*
 * Copyright 2013-2018 the original author or authors.
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
package org.springframework.cloud.sleuth.instrument.opentracing;

import java.util.LinkedHashMap;
import java.util.Map;

import brave.Span;
import brave.Tracer.SpanInScope;
import brave.Tracing;
import brave.opentracing.BraveSpan;
import brave.opentracing.BraveSpanContext;
import brave.opentracing.BraveTracer;
import brave.propagation.CurrentTraceContext;
import brave.propagation.StrictCurrentTraceContext;
import brave.propagation.TraceContext;
import brave.sampler.Sampler;
import io.opentracing.Scope;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMapExtractAdapter;
import io.opentracing.propagation.TextMapInjectAdapter;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.sleuth.util.ArrayListSpanReporter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit4.SpringRunner;
import zipkin2.Annotation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.MapEntry.entry;
import static org.junit.Assert.assertEquals;

/**
 * This shows how one might make an OpenTracing adapter for Brave, and how to navigate in and out of
 * the core concepts.
 *
 * Adopted from: https://github.com/openzipkin-contrib/brave-opentracing/tree/master/src/test/java/brave/opentracing
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
properties = "spring.sleuth.baggage-keys=country-code,user-id")
public class BraveTracerTest {

  @Autowired ArrayListSpanReporter spans;
  @Autowired Tracing brave;
  @Autowired BraveTracer opentracing;

  @Test public void startWithOpenTracingAndFinishWithBrave() {
    io.opentracing.Span openTracingSpan = opentracing.buildSpan("encode")
        .withTag("lc", "codec")
        .withStartTimestamp(1L)
        .start();

    Span braveSpan = ((BraveSpan) openTracingSpan).unwrap();

    braveSpan.annotate(2L, "pump fake");
    braveSpan.finish(3L);

    checkSpanReportedToZipkin();
  }

  @Test public void extractTraceContext() throws Exception {
    Map<String, String> map = new LinkedHashMap<>();
    map.put("X-B3-TraceId", "0000000000000001");
    map.put("X-B3-SpanId", "0000000000000002");
    map.put("X-B3-Sampled", "1");

    BraveSpanContext openTracingContext =
        (BraveSpanContext) opentracing.extract(Format.Builtin.HTTP_HEADERS,
            new TextMapExtractAdapter(map));

    assertThat(openTracingContext.unwrap())
        .isEqualTo(TraceContext.newBuilder()
            .traceId(1L)
            .spanId(2L)
            .sampled(true).build());
  }

  @Test public void extractBaggage() throws Exception {
    Map<String, String> map = new LinkedHashMap<>();
    map.put("X-B3-TraceId", "0000000000000001");
    map.put("X-B3-SpanId", "0000000000000002");
    map.put("X-B3-Sampled", "1");
    map.put("baggage-country-code", "FO");

    BraveSpanContext openTracingContext = opentracing.extract(Format.Builtin.HTTP_HEADERS,
        new TextMapExtractAdapter(map));

    assertThat(openTracingContext.baggageItems())
        .containsExactly(entry("country-code", "FO"));
  }

  @Test public void extractTraceContextTextMap() throws Exception {
    Map<String, String> map = new LinkedHashMap<>();
    map.put("X-B3-TraceId", "0000000000000001");
    map.put("X-B3-SpanId", "0000000000000002");
    map.put("X-B3-Sampled", "1");

    BraveSpanContext openTracingContext =
        (BraveSpanContext) opentracing.extract(Format.Builtin.TEXT_MAP,
            new TextMapExtractAdapter(map));

    assertThat(openTracingContext.unwrap())
        .isEqualTo(TraceContext.newBuilder()
            .traceId(1L)
            .spanId(2L)
            .sampled(true).build());
  }

  @Test public void extractTraceContextCaseInsensitive() throws Exception {
    Map<String, String> map = new LinkedHashMap<>();
    map.put("X-B3-TraceId", "0000000000000001");
    map.put("x-b3-spanid", "0000000000000002");
    map.put("x-b3-SaMpLeD", "1");
    map.put("other", "1");

    BraveSpanContext openTracingContext =
        (BraveSpanContext) opentracing.extract(Format.Builtin.HTTP_HEADERS,
            new TextMapExtractAdapter(map));

    assertThat(openTracingContext.unwrap())
        .isEqualTo(TraceContext.newBuilder()
            .traceId(1L)
            .spanId(2L)
            .sampled(true).build());
  }

  @Test public void injectTraceContext_baggage() throws Exception {
    BraveSpan span = opentracing.buildSpan("foo").start();
    span.setBaggageItem("country-code", "FO");

    Map<String, String> map = new LinkedHashMap<>();
    TextMapInjectAdapter carrier = new TextMapInjectAdapter(map);
    opentracing.inject(span.context(), Format.Builtin.HTTP_HEADERS, carrier);

    assertThat(map).containsEntry("baggage-country-code", "FO");
  }

  void checkSpanReportedToZipkin() {
    assertThat(spans.getSpans()).first().satisfies(s -> {
          assertThat(s.name()).isEqualTo("encode");
          assertThat(s.timestamp()).isEqualTo(1L);
          assertThat(s.annotations())
              .containsExactly(Annotation.create(2L, "pump fake"));
          assertThat(s.tags())
              .containsExactly(entry("lc", "codec"));
          assertThat(s.duration()).isEqualTo(2L);
        }
    );
  }

  @Test public void subsequentChildrenNestProperly_OTStyle() {
    // this test is semantically identical to subsequentChildrenNestProperly_BraveStyle, but uses
    // the OpenTracingAPI instead of the Brave API.

    Long idOfSpanA;
    Long shouldBeIdOfSpanA;
    Long idOfSpanB;
    Long shouldBeIdOfSpanB;
    Long parentIdOfSpanB;
    Long parentIdOfSpanC;

    try (Scope scopeA = opentracing.buildSpan("spanA").startActive(false)) {
      idOfSpanA = getTraceContext(scopeA).spanId();
      try (Scope scopeB = opentracing.buildSpan("spanB").startActive(false)) {
        idOfSpanB = getTraceContext(scopeB).spanId();
        parentIdOfSpanB = getTraceContext(scopeB).parentId();
        shouldBeIdOfSpanB = getTraceContext(opentracing.scopeManager().active()).spanId();
      }
      shouldBeIdOfSpanA = getTraceContext(opentracing.scopeManager().active()).spanId();
      try (Scope scopeC = opentracing.buildSpan("spanC").startActive(false)) {
        parentIdOfSpanC = getTraceContext(scopeC).parentId();
      }
    }

    assertEquals("SpanA should have been active again after closing B", idOfSpanA,
        shouldBeIdOfSpanA);
    assertEquals("SpanB should have been active prior to its closure", idOfSpanB,
        shouldBeIdOfSpanB);
    assertEquals("SpanB's parent should be SpanA", idOfSpanA, parentIdOfSpanB);
    assertEquals("SpanC's parent should be SpanA", idOfSpanA, parentIdOfSpanC);
  }

  @Test public void subsequentChildrenNestProperly_BraveStyle() {
    // this test is semantically identical to subsequentChildrenNestProperly_OTStyle, but uses
    // the Brave API instead of the OpenTracing API.

    Long shouldBeIdOfSpanA;
    Long idOfSpanB;
    Long shouldBeIdOfSpanB;
    Long parentIdOfSpanB;
    Long parentIdOfSpanC;

    Span spanA = brave.tracer().newTrace().name("spanA").start();
    Long idOfSpanA = spanA.context().spanId();
    try (SpanInScope scopeA = brave.tracer().withSpanInScope(spanA)) {

      Span spanB = brave.tracer().newChild(spanA.context()).name("spanB").start();
      idOfSpanB = spanB.context().spanId();
      parentIdOfSpanB = spanB.context().parentId();
      try (SpanInScope scopeB = brave.tracer().withSpanInScope(spanB)) {
        shouldBeIdOfSpanB = brave.currentTraceContext().get().spanId();
      } finally {
        spanB.finish();
      }

      shouldBeIdOfSpanA = brave.currentTraceContext().get().spanId();

      Span spanC = brave.tracer().newChild(spanA.context()).name("spanC").start();
      parentIdOfSpanC = spanC.context().parentId();
      try (SpanInScope scopeC = brave.tracer().withSpanInScope(spanC)) {
        // nothing to do here
      } finally {
        spanC.finish();
      }
    } finally {
      spanA.finish();
    }

    assertEquals("SpanA should have been active again after closing B", idOfSpanA,
        shouldBeIdOfSpanA);
    assertEquals("SpanB should have been active prior to its closure", idOfSpanB,
        shouldBeIdOfSpanB);
    assertEquals("SpanB's parent should be SpanA", idOfSpanA, parentIdOfSpanB);
    assertEquals("SpanC's parent should be SpanA", idOfSpanA, parentIdOfSpanC);
  }

  @Test public void implicitParentFromSpanManager_startActive() {
    try (Scope scopeA = opentracing.buildSpan("spanA").startActive(true)) {
      try (Scope scopeB = opentracing.buildSpan("spanA").startActive(true)) {
        assertThat(getTraceContext(scopeB).parentId())
            .isEqualTo(getTraceContext(scopeA).spanId());
      }
    }
  }

  @Test public void implicitParentFromSpanManager_start() {
    try (Scope scopeA = opentracing.buildSpan("spanA").startActive(true)) {
      BraveSpan span = opentracing.buildSpan("spanB").start();
      assertThat(span.unwrap().context().parentId())
          .isEqualTo(getTraceContext(scopeA).spanId());
    }
  }

  @Test public void implicitParentFromSpanManager_startActive_ignoreActiveSpan() {
    try (Scope scopeA = opentracing.buildSpan("spanA").startActive(true)) {
      try (Scope scopeB = opentracing.buildSpan("spanA")
          .ignoreActiveSpan().startActive(true)) {
        assertThat(getTraceContext(scopeB).parentId())
            .isNull(); // new trace
      }
    }
  }

  @Test public void implicitParentFromSpanManager_start_ignoreActiveSpan() {
    try (Scope scopeA = opentracing.buildSpan("spanA").startActive(true)) {
      BraveSpan span = opentracing.buildSpan("spanB")
          .ignoreActiveSpan().start();
      assertThat(span.unwrap().context().parentId())
          .isNull(); // new trace
    }
  }

  @Test public void ignoresErrorFalseTag_beforeStart() {
    opentracing.buildSpan("encode")
        .withTag("error", false)
        .start().finish();

    assertThat(spans.getSpans().get(0).tags())
        .isEmpty();
  }

  @Test public void ignoresErrorFalseTag_afterStart() {
    opentracing.buildSpan("encode")
        .start()
        .setTag("error", false)
        .finish();

    assertThat(spans.getSpans().get(0).tags())
        .isEmpty();
  }

  private static TraceContext getTraceContext(Scope scope) {
    return ((BraveSpanContext) scope.span().context()).unwrap();
  }

  @Before public void clear() {
    this.spans.clear();
  }

  @Configuration
  @EnableAutoConfiguration
  static class Config {
    @Bean Sampler sampler() {
      return Sampler.ALWAYS_SAMPLE;
    }

    @Bean ArrayListSpanReporter reporter() {
      return new ArrayListSpanReporter();
    }

    @Bean CurrentTraceContext currentTraceContext() {
      return new StrictCurrentTraceContext();
    }
  }
}
