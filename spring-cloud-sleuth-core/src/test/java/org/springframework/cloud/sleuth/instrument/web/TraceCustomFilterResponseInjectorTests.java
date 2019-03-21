/*
 * Copyright 2013-2017 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.web;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.embedded.EmbeddedServletContainerInitializedEvent;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanTextMap;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.filter.GenericFilterBean;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import static org.assertj.core.api.BDDAssertions.then;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = TraceCustomFilterResponseInjectorTests.Config.class,
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext
public class TraceCustomFilterResponseInjectorTests {
	@Autowired RestTemplate restTemplate;
	@Autowired Config config;
	@Autowired CustomRestController customRestController;


	@Test
	@SuppressWarnings("unchecked")
	public void should_inject_trace_and_span_ids_in_response_headers() {
		RequestEntity<?> requestEntity = RequestEntity
				.get(URI.create("http://localhost:" + this.config.port + "/headers"))
				.build();

		@SuppressWarnings("rawtypes")
		ResponseEntity<Map> responseEntity = this.restTemplate.exchange(requestEntity, Map.class);

		then(responseEntity.getHeaders())
				.containsKeys(Span.TRACE_ID_NAME, Span.SPAN_ID_NAME)
				.as("Trace headers must be present in response headers");
	}

	@Configuration
	@EnableAutoConfiguration
	static class Config
			implements ApplicationListener<EmbeddedServletContainerInitializedEvent> {
		int port;

		// tag::configuration[]
		@Bean HttpSpanInjector customHttpServletResponseSpanInjector() {
			return new CustomHttpServletResponseSpanInjector();
		}

		@Bean
		HttpResponseInjectingTraceFilter responseInjectingTraceFilter(Tracer tracer) {
			return new HttpResponseInjectingTraceFilter(tracer, customHttpServletResponseSpanInjector());
		}
		// end::configuration[]

		@Override
		public void onApplicationEvent(EmbeddedServletContainerInitializedEvent event) {
			this.port = event.getEmbeddedServletContainer().getPort();
		}

		@Bean
		public RestTemplate restTemplate() {
			return new RestTemplate();
		}

		@Bean
		CustomRestController customRestController() {
			return new CustomRestController();
		}


	}

	// tag::injector[]
	static class CustomHttpServletResponseSpanInjector extends ZipkinHttpSpanInjector {

		@Override
		public void inject(Span span, SpanTextMap carrier) {
			super.inject(span, carrier);
			carrier.put(Span.TRACE_ID_NAME, span.traceIdString());
			carrier.put(Span.SPAN_ID_NAME, Span.idToHex(span.getSpanId()));
		}
	}

	static class HttpResponseInjectingTraceFilter extends GenericFilterBean {

		private final Tracer tracer;
		private final HttpSpanInjector spanInjector;

		public HttpResponseInjectingTraceFilter(Tracer tracer, HttpSpanInjector spanInjector) {
			this.tracer = tracer;
			this.spanInjector = spanInjector;
		}

		@Override
		public void doFilter(ServletRequest request, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
			HttpServletResponse response = (HttpServletResponse) servletResponse;
			Span currentSpan = this.tracer.getCurrentSpan();
			this.spanInjector.inject(currentSpan, new HttpServletResponseTextMap(response));
			filterChain.doFilter(request, response);
		}

		 class HttpServletResponseTextMap implements SpanTextMap {

			 private final HttpServletResponse delegate;

			 HttpServletResponseTextMap(HttpServletResponse delegate) {
				 this.delegate = delegate;
			 }

			 @Override
			 public Iterator<Map.Entry<String, String>> iterator() {
				 Map<String, String> map = new HashMap<>();
				 for (String header : this.delegate.getHeaderNames()) {
					map.put(header, this.delegate.getHeader(header));
				 }
				 return map.entrySet().iterator();
			 }

			 @Override
			 public void put(String key, String value) {
				this.delegate.addHeader(key, value);
			 }
		 }
	}
	// end::injector[]

	@RestController
	static class CustomRestController {

		@RequestMapping("/headers")
		public Map<String, String> headers(@RequestHeader HttpHeaders headers) {
			Map<String, String> map = new HashMap<>();
			for (String key : headers.keySet()) {
				map.put(key, headers.getFirst(key));
			}
			return map;
		}
	}
}
