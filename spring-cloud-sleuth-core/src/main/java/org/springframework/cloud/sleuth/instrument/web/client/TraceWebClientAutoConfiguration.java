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

package org.springframework.cloud.sleuth.instrument.web.client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import brave.Span;
import brave.Tracer;
import brave.http.HttpClientHandler;
import brave.http.HttpTracing;
import brave.httpasyncclient.TracingHttpAsyncClientBuilder;
import brave.httpclient.TracingHttpClientBuilder;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import brave.spring.web.TracingClientHttpRequestInterceptor;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.reactivestreams.Publisher;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.cloud.commons.httpclient.HttpClientConfiguration;
import org.springframework.cloud.sleuth.instrument.web.TraceWebServletAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.ipc.netty.http.client.HttpClient;
import reactor.ipc.netty.http.client.HttpClientRequest;
import reactor.ipc.netty.http.client.HttpClientResponse;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration
 * Auto-configuration} enables span information propagation when using
 * {@link RestTemplate}
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
@Configuration
@SleuthWebClientEnabled
@ConditionalOnBean(HttpTracing.class)
@AutoConfigureAfter(TraceWebServletAutoConfiguration.class)
@AutoConfigureBefore(HttpClientConfiguration.class)
public class TraceWebClientAutoConfiguration {

	@Configuration
	@ConditionalOnClass(RestTemplate.class)
	static class RestTemplateConfig {

		@Bean
		public TracingClientHttpRequestInterceptor tracingClientHttpRequestInterceptor(HttpTracing httpTracing) {
			return (TracingClientHttpRequestInterceptor) TracingClientHttpRequestInterceptor.create(httpTracing);
		}

		@Configuration
		protected static class TraceInterceptorConfiguration {

			@Autowired private TracingClientHttpRequestInterceptor clientInterceptor;

			@Bean @Order RestTemplateCustomizer traceRestTemplateCustomizer() {
				return new TraceRestTemplateCustomizer(this.clientInterceptor);
			}

			@Bean static TraceRestTemplateBeanPostProcessor traceRestTemplateBPP(ListableBeanFactory beanFactory) {
				return new TraceRestTemplateBeanPostProcessor(beanFactory);
			}
		}
	}

	@Configuration
	@ConditionalOnClass(HttpClientBuilder.class)
	static class HttpClientBuilderConfig {

		@Bean
		@ConditionalOnMissingBean
		HttpClientBuilder traceHttpClientBuilder(HttpTracing httpTracing) {
			return TracingHttpClientBuilder.create(httpTracing);
		}
	}

	@Configuration
	@ConditionalOnClass(HttpAsyncClientBuilder.class)
	static class HttpAsyncClientBuilderConfig {

		@Bean
		@ConditionalOnMissingBean
		HttpAsyncClientBuilder traceHttpAsyncClientBuilder(HttpTracing httpTracing) {
			return TracingHttpAsyncClientBuilder.create(httpTracing);
		}
	}

	@ConditionalOnClass(WebClient.class)
	static class WebClientConfig {

		@Bean static TraceWebClientBeanPostProcessor traceWebClientBeanPostProcessor(BeanFactory beanFactory) {
			return new TraceWebClientBeanPostProcessor(beanFactory);
		}
	}

	@Configuration
	@ConditionalOnClass(HttpClient.class)
	static class NettyConfiguration {
		@Bean
		public NettyAspect traceNetyAspect(HttpTracing httpTracing) {
			return new NettyAspect(httpTracing);
		}
	}
}

class RestTemplateInterceptorInjector {
	private final ClientHttpRequestInterceptor interceptor;

	RestTemplateInterceptorInjector(ClientHttpRequestInterceptor interceptor) {
		this.interceptor = interceptor;
	}

	void inject(RestTemplate restTemplate) {
		if (hasTraceInterceptor(restTemplate)) {
			return;
		}
		List<ClientHttpRequestInterceptor> interceptors = new ArrayList<ClientHttpRequestInterceptor>(
				restTemplate.getInterceptors());
		interceptors.add(0, this.interceptor);
		restTemplate.setInterceptors(interceptors);
	}

	private boolean hasTraceInterceptor(RestTemplate restTemplate) {
		for (ClientHttpRequestInterceptor interceptor : restTemplate
				.getInterceptors()) {
			if (interceptor instanceof TracingClientHttpRequestInterceptor) {
				return true;
			}
		}
		return false;
	}
}

class TraceRestTemplateCustomizer implements RestTemplateCustomizer {

	private final TracingClientHttpRequestInterceptor interceptor;

	TraceRestTemplateCustomizer(TracingClientHttpRequestInterceptor interceptor) {
		this.interceptor = interceptor;
	}

	@Override public void customize(RestTemplate restTemplate) {
		new RestTemplateInterceptorInjector(this.interceptor)
				.inject(restTemplate);
	}
}

class TraceRestTemplateBeanPostProcessor implements BeanPostProcessor {

	private final BeanFactory beanFactory;

	TraceRestTemplateBeanPostProcessor(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}

	@Override public Object postProcessBeforeInitialization(Object bean, String beanName)
			throws BeansException {
		return bean;
	}

	@Override public Object postProcessAfterInitialization(Object bean, String beanName)
			throws BeansException {
		if (bean instanceof RestTemplate)  {
			RestTemplate rt = (RestTemplate) bean;
			new RestTemplateInterceptorInjector(interceptor()).inject(rt);
		}
		return bean;
	}

	private LazyTracingClientHttpRequestInterceptor interceptor() {
		return new LazyTracingClientHttpRequestInterceptor(this.beanFactory);
	}

}

class LazyTracingClientHttpRequestInterceptor implements ClientHttpRequestInterceptor {

	private final BeanFactory beanFactory;
	private TracingClientHttpRequestInterceptor interceptor;

	public LazyTracingClientHttpRequestInterceptor(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}

	@Override public ClientHttpResponse intercept(HttpRequest request, byte[] body,
			ClientHttpRequestExecution execution) throws IOException {
		return interceptor().intercept(request, body, execution);
	}

	private TracingClientHttpRequestInterceptor interceptor() {
		if (this.interceptor == null) {
			this.interceptor = this.beanFactory.getBean(TracingClientHttpRequestInterceptor.class);
		}
		return this.interceptor;
	}
}

@Aspect
class NettyAspect {

	private final TracingHttpClientInstrumentation instrumentation;

	NettyAspect(HttpTracing httpTracing) {
		this.instrumentation = TracingHttpClientInstrumentation.create(httpTracing);
	}

	@Pointcut("execution(public * reactor.ipc.netty.http.client.HttpClient.request(..)) && args(method, url, handler)")
	private void anyHttpClientRequestSending(HttpMethod method,
			String url, Function<? super HttpClientRequest, ? extends Publisher<Void>> handler) { } // NOSONAR

	@Around("anyHttpClientRequestSending(method, url, handler)")
	public Object wrapHttpClientRequestSending(ProceedingJoinPoint pjp,
			HttpMethod method,
			String url, Function<? super HttpClientRequest, ? extends Publisher<Void>> handler) throws Throwable {
		return this.instrumentation.wrapHttpClientRequestSending(pjp, method, url, handler);
	}
}

class TracingHttpClientInstrumentation {
	private static final Log log = LogFactory.getLog(TracingHttpClientInstrumentation.class);

	static final Propagation.Setter<HttpHeaders, String> SETTER = new Propagation.Setter<HttpHeaders, String>() {
		@Override public void put(HttpHeaders carrier, String key, String value) {
			if (!carrier.contains(key)) {
				carrier.add(key, value);
			}
		}

		@Override public String toString() {
			return "HttpHeaders::add";
		}
	};

	static final Propagation.Getter<HttpHeaders, String> GETTER = new Propagation.Getter<HttpHeaders, String>() {
		@Override public String get(HttpHeaders carrier, String key) {
			return carrier.get(key);
		}

		@Override public String toString() {
			return "HttpHeaders::get";
		}
	};

	static TracingHttpClientInstrumentation create(HttpTracing httpTracing) {
		return new TracingHttpClientInstrumentation(httpTracing);
	}

	final Tracer tracer;
	final HttpClientHandler<HttpClientRequest, HttpClientResponse> handler;
	final TraceContext.Injector<HttpHeaders> injector;
	final HttpTracing httpTracing;

	TracingHttpClientInstrumentation(HttpTracing httpTracing) {
		this.tracer = httpTracing.tracing().tracer();
		this.handler = HttpClientHandler.create(httpTracing, new HttpAdapter());
		this.injector = httpTracing.tracing().propagation().injector(SETTER);
		this.httpTracing = httpTracing;
	}

	Object wrapHttpClientRequestSending(ProceedingJoinPoint pjp,
			HttpMethod method,
			String url, Function<? super HttpClientRequest, ? extends Publisher<Void>> handler) throws Throwable {
		// add headers and set CS
		final Span currentSpan = this.tracer.currentSpan();
		final AtomicReference<Span> span = new AtomicReference<>();
		final AtomicBoolean requestAlreadyInstrumented = new AtomicBoolean();
		Function<HttpClientRequest, Publisher<Void>> combinedFunction =
				req -> {
					try (Tracer.SpanInScope spanInScope = this.tracer.withSpanInScope(currentSpan)) {
						io.netty.handler.codec.http.HttpHeaders headers = req
								.requestHeaders();
						TraceContextOrSamplingFlags flags = this.httpTracing.tracing()
								.propagation().extractor(GETTER).extract(headers);
						if (flags != TraceContextOrSamplingFlags.EMPTY) {
							requestAlreadyInstrumented.set(true);
							if (log.isDebugEnabled()) {
								log.debug("Request already instrumented. Skipping");
							}
							return handle(handler, req);
						}
						span.set(this.handler.handleSend(this.injector, headers, req));
						try (Tracer.SpanInScope clientInScope = this.tracer.withSpanInScope(span.get())) {
							if (log.isDebugEnabled()) {
								log.debug("Created a new client span for Netty client");
							}
							return handle(handler, req);
						}
					}
				};
		// run
		Mono<HttpClientResponse> responseMono =
				(Mono<HttpClientResponse>) pjp.proceed(new Object[] { method , url, combinedFunction });
		// get response
		return responseMono.doOnSuccessOrError((httpClientResponse, throwable) -> {
			if (requestAlreadyInstrumented.get()) {
				if (log.isDebugEnabled()) {
					log.debug("Request already instrumented. Skipping");
					return;
				}
			}
			try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(span.get())) {
				// status codes and CR
				this.handler.handleReceive(httpClientResponse, throwable, span.get());
				if (log.isDebugEnabled()) {
					log.debug("Setting client sent spans");
				}
			}
		});
	}

	private Publisher<Void> handle(
			Function<? super HttpClientRequest, ? extends Publisher<Void>> handler,
			HttpClientRequest req) {
		if (handler != null) {
			return handler.apply(req);
		}
		return req;
	}

	static final class HttpAdapter
			extends brave.http.HttpClientAdapter<HttpClientRequest, HttpClientResponse> {

		@Override public String method(HttpClientRequest request) {
			return request.method().name();
		}

		@Override public String url(HttpClientRequest request) {
			return request.uri();
		}

		@Override public String requestHeader(HttpClientRequest request, String name) {
			Object result = request.requestHeaders().get(name);
			return result != null ? result.toString() : "";
		}

		@Override public Integer statusCode(HttpClientResponse response) {
			return response.status().code();
		}
	}
}
