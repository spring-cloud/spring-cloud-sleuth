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

package org.springframework.cloud.sleuth.brave.instrument.web;

import java.io.UnsupportedEncodingException;

import javax.servlet.http.HttpServletRequest;

import brave.Span.Kind;
import brave.handler.MutableSpan;
import brave.handler.SpanHandler;
import brave.propagation.CurrentTraceContext;
import brave.sampler.Sampler;
import brave.test.IntegrationTestSpanHandler;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.ClassRule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.cloud.sleuth.SpanCustomizer;
import org.springframework.cloud.sleuth.instrument.web.mvc.HandlerParser;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.filter.AbstractRequestLoggingFilter;
import org.springframework.web.filter.CommonsRequestLoggingFilter;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.WebUtils;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author Wu Wen
 */
@SpringBootTest(classes = TraceCustomHandlerParserPostHandleWebIntegrationTests.Config.class,
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(OutputCaptureExtension.class)
public class TraceCustomHandlerParserPostHandleWebIntegrationTests {

	@ClassRule
	public static IntegrationTestSpanHandler spanHandler = new IntegrationTestSpanHandler();

	@Autowired
	CurrentTraceContext currentTraceContext;

	@Autowired
	Environment environment;

	@Test
	public void should_tag_payload() {

		new RestTemplate().postForEntity("http://localhost:" + port() + "/user", new UserController.User("WuWen"),
				UserController.User.class);

		then(this.currentTraceContext.get()).isNull();
		MutableSpan span = spanHandler.takeRemoteSpan(Kind.SERVER);

		then(span.tags()).containsEntry("preHandle.payload.isNull", "null");
		then(span.tags()).containsEntry("http.payload", "{\"name\":\"WuWen\"}");
	}

	private int port() {
		return this.environment.getProperty("local.server.port", Integer.class);
	}

	@EnableAutoConfiguration(
			// spring boot test will otherwise instrument the client and server with the
			// same bean factory which isn't expected
			excludeName = "org.springframework.cloud.sleuth.brave.instrument.web.client.TraceWebClientAutoConfiguration")
	@Configuration(proxyBeanMethods = false)
	public static class Config {

		@Bean
		UserController goodController() {
			return new UserController();
		}

		@Bean
		SpanHandler testSpanHandler() {
			return spanHandler;
		}

		@Bean
		Sampler alwaysSampler() {
			return Sampler.ALWAYS_SAMPLE;
		}

		@Bean
		CommonsRequestLoggingFilter commonsRequestLoggingFilter() {
			CommonsRequestLoggingFilter filter = new CommonsRequestLoggingFilter();
			filter.setIncludePayload(true);
			filter.setMaxPayloadLength(512);
			return filter;
		}

		@Bean
		public FilterRegistrationBean requestLoggingFilter(CommonsRequestLoggingFilter filter) {
			FilterRegistrationBean<AbstractRequestLoggingFilter> registration = new FilterRegistrationBean<>();
			registration.addUrlPatterns("/*");
			registration.setFilter(filter);
			return registration;
		}

		@Bean
		HandlerParser handlerParser() {
			return new HandlerParser() {

				@Override
				protected void preHandle(HttpServletRequest request, Object handler, SpanCustomizer customizer) {
					customizer.tag("preHandle.payload.isNull", getMessagePayload(request));
				}

				@Override
				protected void postHandle(HttpServletRequest request, Object handler, ModelAndView modelAndView,
						SpanCustomizer customizer) {
					customizer.tag("http.payload", getMessagePayload(request));
				}

				protected String getMessagePayload(HttpServletRequest request) {
					ContentCachingRequestWrapper wrapper = WebUtils.getNativeRequest(request,
							ContentCachingRequestWrapper.class);
					if (wrapper != null) {
						byte[] buf = wrapper.getContentAsByteArray();
						if (buf.length > 0) {
							int length = Math.min(buf.length, 512);
							try {
								return new String(buf, 0, length, wrapper.getCharacterEncoding());
							}
							catch (UnsupportedEncodingException ex) {
								return "[unknown]";
							}
						}
					}
					return "null";
				}
			};
		}

		@Bean
		RestTemplate restTemplate() {
			RestTemplate restTemplate = new RestTemplate();
			restTemplate.setErrorHandler(new DefaultResponseErrorHandler() {
				@Override
				public void handleError(ClientHttpResponse response) {
				}
			});
			return restTemplate;
		}

	}

	@RestController
	public static class UserController {

		private static final Log log = LogFactory.getLog(UserController.class);

		@PostMapping("/user")
		public User user(@RequestBody User user) {
			log.info("Test request body.");
			return user;
		}

		public static class User {

			private String name;

			public User() {
			}

			public User(String name) {
				this.name = name;
			}

			public String getName() {
				return name;
			}

			public void setName(String name) {
				this.name = name;
			}

			@Override
			public String toString() {
				return "User{" + "name='" + name + '\'' + '}';
			}

		}

	}

}
