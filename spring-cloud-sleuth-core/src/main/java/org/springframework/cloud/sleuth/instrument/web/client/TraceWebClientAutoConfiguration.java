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
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

import brave.Span;
import brave.Tracer;
import brave.http.HttpClientHandler;
import brave.http.HttpTracing;
import brave.httpasyncclient.TracingHttpAsyncClientBuilder;
import brave.httpclient.TracingHttpClientBuilder;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import brave.spring.web.TracingClientHttpRequestInterceptor;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.util.AttributeKey;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
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
import org.springframework.boot.autoconfigure.security.oauth2.resource.UserInfoRestTemplateCustomizer;
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
import org.springframework.security.oauth2.client.OAuth2RestTemplate;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.NettyOutbound;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.HttpClientRequest;
import reactor.netty.http.client.HttpClientResponse;

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
		public NettyClientBeanPostProcessor nettyClientBeanPostProcessor(BeanFactory beanFactory) {
			return new NettyClientBeanPostProcessor(beanFactory);
		}
	}

	@Configuration
	@ConditionalOnClass({ UserInfoRestTemplateCustomizer.class, OAuth2RestTemplate.class })
	protected static class TraceOAuthConfiguration {

		@Bean
		UserInfoRestTemplateCustomizerBPP userInfoRestTemplateCustomizerBeanPostProcessor(BeanFactory beanFactory) {
			return new UserInfoRestTemplateCustomizerBPP(beanFactory);
		}

		@Bean
		@ConditionalOnMissingBean
		UserInfoRestTemplateCustomizer traceUserInfoRestTemplateCustomizer(BeanFactory beanFactory) {
			return new TraceUserInfoRestTemplateCustomizer(beanFactory);
		}

		private static class UserInfoRestTemplateCustomizerBPP implements BeanPostProcessor {

			private final BeanFactory beanFactory;

			UserInfoRestTemplateCustomizerBPP(BeanFactory beanFactory) {
				this.beanFactory = beanFactory;
			}

			@Override
			public Object postProcessBeforeInitialization(Object bean,
					String beanName) throws BeansException {
				return bean;
			}

			@Override
			public Object postProcessAfterInitialization(final Object bean,
					String beanName) throws BeansException {
				final BeanFactory beanFactory = this.beanFactory;
				if (bean instanceof UserInfoRestTemplateCustomizer &&
						!(bean instanceof TraceUserInfoRestTemplateCustomizer)) {
					return new TraceUserInfoRestTemplateCustomizer(beanFactory, bean);
				}
				return bean;
			}
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

class NettyClientBeanPostProcessor implements BeanPostProcessor {

	private final BeanFactory beanFactory;

	NettyClientBeanPostProcessor(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}

	@Override public Object postProcessAfterInitialization(Object bean, String beanName)
			throws BeansException {
		if (bean instanceof HttpClient) {
			HttpClient httpClient = (HttpClient) bean;
			return TracingHttpClientInstrumentation.create(this.beanFactory).wrap(httpClient);
		}
		return bean;
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

	static TracingHttpClientInstrumentation create(BeanFactory beanFactory) {
		return new TracingHttpClientInstrumentation(beanFactory);
	}

	final BeanFactory beanFactory;
	Tracer tracer;
	HttpClientHandler<HttpClientRequest, HttpClientResponse> handler;
	TraceContext.Injector<HttpHeaders> injector;
	HttpTracing httpTracing;

	TracingHttpClientInstrumentation(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}

	private HttpTracing httpTracing() {
		if (this.httpTracing == null) {
			this.httpTracing = this.beanFactory.getBean(HttpTracing.class);
		}
		return this.httpTracing;
	}

	private Tracer tracer() {
		if (this.tracer == null) {
			this.tracer = httpTracing().tracing().tracer();
		}
		return this.tracer;
	}

	private HttpClientHandler<HttpClientRequest, HttpClientResponse> handler() {
		if (this.handler == null) {
			this.handler = HttpClientHandler.create(httpTracing(), new HttpAdapter());
		}
		return this.handler;
	}

	private TraceContext.Injector<HttpHeaders> injector() {
		if (this.injector == null) {
			this.injector = httpTracing().tracing().propagation().injector(SETTER);
		}
		return this.injector;
	}

	private AttributeKey<Span> spanAttribute() {
		return AttributeKey.valueOf("span");
	}

	HttpClient wrap(HttpClient httpClient) {
		return httpClient
				.tcpConfiguration(tcp -> tcp.bootstrap(c ->
						c.attr(spanAttribute(), tracer().currentSpan())))
				.doOnRequest((httpClientRequest, connection) -> {
					Span spanFromAttribute = connection.channel().attr(spanAttribute()).get();
					try (Tracer.SpanInScope spanInScope = tracer().withSpanInScope(spanFromAttribute)) {
						handler().handleSend(injector(), httpClientRequest.requestHeaders(), httpClientRequest);
						if (log.isDebugEnabled()) {
							log.debug("Handled send of " + spanFromAttribute);
						}
					}
				})
				.doAfterResponse((httpClientResponse, connection) -> {
					Span spanFromAttribute = connection.channel().attr(spanAttribute()).getAndSet(null);
					try (Tracer.SpanInScope ws = tracer().withSpanInScope(spanFromAttribute)) {
						if (spanFromAttribute != null) {
							// TODO: What about throwable
							handler().handleReceive(httpClientResponse, null, spanFromAttribute);
							if (log.isDebugEnabled()) {
								log.debug("Setting client sent spans");
							}
						}
					}
				});
	}

	// TODO: What about the gateway
	/**
	 * The `org.springframework.cloud.gateway.filter.NettyRoutingFilter` in SC Gateway
	 * is adding only these headers that were set when the request came in. That means
	 * that adding any additional headers (via instrumentation) is completely ignored.
	 * That's why we're wrapping the `HttpClientRequest` in such a wrapper that
	 * when `setHeaders` is called (that clears any current headers), will also add
	 * the tracing headers
	 */
	static class TracedHttpClientRequest implements HttpClientRequest {
		private HttpClientRequest delegate;
		private final io.netty.handler.codec.http.HttpHeaders addedHeaders;

		TracedHttpClientRequest(HttpClientRequest delegate, HttpHeaders addedHeaders) {
			this.delegate = delegate;
			this.addedHeaders = addedHeaders;
		}

		@Override public HttpClientRequest addCookie(Cookie cookie) {
			this.delegate = this.delegate.addCookie(cookie);
			return this;
		}

		@Override public HttpClientRequest addHeader(CharSequence name,
				CharSequence value) {
			this.delegate = this.delegate.addHeader(name, value);
			return this;
		}

		@Override public boolean hasSentHeaders() {
			return this.delegate.hasSentHeaders();
		}

		@Override public HttpClientRequest header(CharSequence name, CharSequence value) {
			this.delegate = this.delegate.header(name, value);
			return this;
		}

		@Override public HttpClientRequest headers(HttpHeaders headers) {
			HttpHeaders copy = headers.copy();
			copy.add(this.addedHeaders);
			this.delegate = this.delegate.headers(copy);
			return this;
		}

		@Override public boolean isFollowRedirect() {
			return this.delegate.isFollowRedirect();
		}

		@Override public HttpClientRequest keepAlive(boolean keepAlive) {
			this.delegate = this.delegate.keepAlive(keepAlive);
			return this;
		}

		@Override public String[] redirectedFrom() {
			return this.delegate.redirectedFrom();
		}

		@Override public HttpHeaders requestHeaders() {
			return this.delegate.requestHeaders();
		}

		@Override public Map<CharSequence, Set<Cookie>> cookies() {
			return this.delegate.cookies();
		}

		@Override public boolean isKeepAlive() {
			return this.delegate.isKeepAlive();
		}

		@Override public boolean isWebsocket() {
			return this.delegate.isWebsocket();
		}

		@Override public HttpMethod method() {
			return this.delegate.method();
		}

		@Override public String path() {
			return this.delegate.path();
		}

		@Override public String uri() {
			return this.delegate.uri();
		}

		@Override public HttpVersion version() {
			return this.delegate.version();
		}
	}

	private Publisher<Void> handle(
			BiFunction<? super HttpClientRequest, ? super NettyOutbound, ? extends Publisher<Void>> handler,
			HttpClientRequest req, NettyOutbound nettyOutbound) {
		if (handler != null) {
			return handler.apply(req, nettyOutbound);
		}
		return nettyOutbound;
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

class TraceUserInfoRestTemplateCustomizer implements UserInfoRestTemplateCustomizer {

	private final BeanFactory beanFactory;
	private final Object delegate;

	TraceUserInfoRestTemplateCustomizer(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
		this.delegate = null;
	}

	TraceUserInfoRestTemplateCustomizer(BeanFactory beanFactory, Object bean) {
		this.beanFactory = beanFactory;
		this.delegate = bean;
	}

	@Override public void customize(OAuth2RestTemplate template) {
		final TracingClientHttpRequestInterceptor interceptor =
				this.beanFactory.getBean(TracingClientHttpRequestInterceptor.class);
		new RestTemplateInterceptorInjector(interceptor).inject(template);
		if (this.delegate != null) {
			((UserInfoRestTemplateCustomizer) this.delegate).customize(template);
		}
	}
}
