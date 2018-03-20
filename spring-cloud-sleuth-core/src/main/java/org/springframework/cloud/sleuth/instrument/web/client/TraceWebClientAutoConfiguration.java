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
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import brave.Span;
import brave.Tracer;
import brave.http.HttpClientHandler;
import brave.http.HttpTracing;
import brave.httpasyncclient.TracingHttpAsyncClientBuilder;
import brave.httpclient.TracingHttpClientBuilder;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import brave.spring.web.TracingClientHttpRequestInterceptor;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.cookie.Cookie;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.ipc.netty.NettyContext;
import reactor.ipc.netty.NettyOutbound;
import reactor.ipc.netty.NettyPipeline;
import reactor.ipc.netty.channel.data.FileChunkedStrategy;
import reactor.ipc.netty.http.client.HttpClient;
import reactor.ipc.netty.http.client.HttpClientRequest;
import reactor.ipc.netty.http.client.HttpClientResponse;
import reactor.ipc.netty.http.websocket.WebsocketOutbound;

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
		return Mono.defer(() -> {
			try {
				return this.instrumentation.wrapHttpClientRequestSending(pjp, method, url, handler);
			}
			catch (Throwable e) {
				return Mono.error(e);
			}
		});
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

	Mono<HttpClientResponse> wrapHttpClientRequestSending(ProceedingJoinPoint pjp,
			HttpMethod method,
			String url, Function<? super HttpClientRequest, ? extends Publisher<Void>> handler) throws Throwable {
		// add headers and set CS
		final Span currentSpan = this.tracer.currentSpan();
		final AtomicReference<Span> span = new AtomicReference<>();
		Function<HttpClientRequest, Publisher<Void>> combinedFunction =
				req -> {
					try (Tracer.SpanInScope spanInScope = this.tracer.withSpanInScope(currentSpan)) {
						io.netty.handler.codec.http.HttpHeaders originalHeaders = req
								.requestHeaders().copy();
						io.netty.handler.codec.http.HttpHeaders tracedHeaders = req
								.requestHeaders();
						span.set(this.handler.handleSend(this.injector, tracedHeaders, req));
						io.netty.handler.codec.http.HttpHeaders addedHeaders = tracedHeaders.copy();
						originalHeaders.forEach(header -> addedHeaders.remove(header.getKey()));
						try (Tracer.SpanInScope clientInScope = this.tracer.withSpanInScope(span.get())) {
							if (log.isDebugEnabled()) {
								log.debug("Created a new client span for Netty client");
							}
							return handle(handler, new TracedHttpClientRequest(req, addedHeaders));
						}
					}
				};
		// run
		Mono<HttpClientResponse> responseMono =
				(Mono<HttpClientResponse>) pjp.proceed(new Object[] { method , url, combinedFunction });
		// get response
		return responseMono.doOnSuccessOrError((httpClientResponse, throwable) -> {
			try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(span.get())) {
				// status codes and CR
				this.handler.handleReceive(httpClientResponse, throwable, span.get());
				if (log.isDebugEnabled()) {
					log.debug("Setting client sent spans");
				}
			}
		});
	}

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

		@Override public HttpClientRequest context(
				Consumer<NettyContext> contextCallback) {
			this.delegate = this.delegate.context(contextCallback);
			return this;
		}

		@Override public HttpClientRequest chunkedTransfer(boolean chunked) {
			this.delegate = this.delegate.chunkedTransfer(chunked);
			return this;
		}

		@Override public HttpClientRequest options(
				Consumer<? super NettyPipeline.SendOptions> configurator) {
			this.delegate = this.delegate.options(configurator);
			return this;
		}

		@Override public HttpClientRequest followRedirect() {
			this.delegate = this.delegate.followRedirect();
			return this;
		}

		@Override public HttpClientRequest failOnClientError(boolean shouldFail) {
			this.delegate = this.delegate.failOnClientError(shouldFail);
			return this;
		}

		@Override public HttpClientRequest failOnServerError(boolean shouldFail) {
			this.delegate = this.delegate.failOnServerError(shouldFail);
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

		@Override public HttpClientRequest onWriteIdle(long idleTimeout,
				Runnable onWriteIdle) {
			this.delegate = this.delegate.onWriteIdle(idleTimeout, onWriteIdle);
			return this;
		}

		@Override public String[] redirectedFrom() {
			return this.delegate.redirectedFrom();
		}

		@Override public HttpHeaders requestHeaders() {
			return this.delegate.requestHeaders();
		}

		@Override public Mono<Void> send() {
			return this.delegate.send();
		}

		@Override public Flux<Long> sendForm(Consumer<Form> formCallback) {
			return this.delegate.sendForm(formCallback);
		}

		@Override public NettyOutbound sendHeaders() {
			return this.delegate.sendHeaders();
		}

		@Override public WebsocketOutbound sendWebsocket() {
			return this.delegate.sendWebsocket();
		}

		@Override public WebsocketOutbound sendWebsocket(String subprotocols) {
			return this.delegate.sendWebsocket(subprotocols);
		}

		@Override public ByteBufAllocator alloc() {
			return this.delegate.alloc();
		}

		@Override public NettyContext context() {
			return this.delegate.context();
		}

		@Override public FileChunkedStrategy getFileChunkedStrategy() {
			return this.delegate.getFileChunkedStrategy();
		}

		@Override public Mono<Void> neverComplete() {
			return this.delegate.neverComplete();
		}

		@Override public NettyOutbound send(Publisher<? extends ByteBuf> dataStream) {
			return this.delegate.send(dataStream);
		}

		@Override public NettyOutbound sendByteArray(
				Publisher<? extends byte[]> dataStream) {
			return this.delegate.sendByteArray(dataStream);
		}

		@Override public NettyOutbound sendFile(Path file) {
			return this.delegate.sendFile(file);
		}

		@Override public NettyOutbound sendFile(Path file, long position, long count) {
			return this.delegate.sendFile(file, position, count);
		}

		@Override public NettyOutbound sendFileChunked(Path file, long position,
				long count) {
			return this.delegate.sendFileChunked(file, position, count);
		}

		@Override public NettyOutbound sendGroups(
				Publisher<? extends Publisher<? extends ByteBuf>> dataStreams) {
			return this.delegate.sendGroups(dataStreams);
		}

		@Override public NettyOutbound sendObject(Publisher<?> dataStream) {
			return this.delegate.sendObject(dataStream);
		}

		@Override public NettyOutbound sendObject(Object msg) {
			return this.delegate.sendObject(msg);
		}

		@Override public NettyOutbound sendString(
				Publisher<? extends String> dataStream) {
			return this.delegate.sendString(dataStream);
		}

		@Override public NettyOutbound sendString(Publisher<? extends String> dataStream,
				Charset charset) {
			return this.delegate.sendString(dataStream, charset);
		}

		@Override public void subscribe(Subscriber<? super Void> s) {
			this.delegate.subscribe(s);
		}

		@Override public Mono<Void> then() {
			return this.delegate.then();
		}

		@Override public NettyOutbound then(Publisher<Void> other) {
			return this.delegate.then(other);
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
