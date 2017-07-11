package org.springframework.cloud.sleuth.instrument.web;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.cloud.sleuth.ErrorParser;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanReporter;
import org.springframework.cloud.sleuth.TraceKeys;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.cloud.sleuth.sampler.NeverSampler;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * A {@link WebFilter} that creates / continues / closes and detaches spans
 * for a reactive web application.
 *
 * @author Marcin Grzejszczak
 * @since 2.0.0
 */
public class TraceWebFilter implements WebFilter {

	private static final Log log = LogFactory.getLog(TraceWebFilter.class);

	protected static final String TRACE_REQUEST_ATTR = TraceWebFilter.class.getName()
			+ ".TRACE";
	private static final String HTTP_COMPONENT = "http";

	private Tracer tracer;
	private TraceKeys traceKeys;
	private final Pattern skipPattern;
	private SpanReporter spanReporter;
	private HttpSpanExtractor spanExtractor;
	private HttpTraceKeysInjector httpTraceKeysInjector;
	private ErrorParser errorParser;
	private final BeanFactory beanFactory;

	TraceWebFilter(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
		this.skipPattern = Pattern.compile(SleuthWebProperties.DEFAULT_SKIP_PATTERN);
	}

	TraceWebFilter(BeanFactory beanFactory, Pattern skipPattern) {
		this.beanFactory = beanFactory;
		this.skipPattern = skipPattern;
	}

	@Override public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		ServerHttpRequest request = exchange.getRequest();
		ServerHttpResponse response = exchange.getResponse();
		String uri = request.getPath().pathWithinApplication().value();
		boolean skip = this.skipPattern.matcher(uri).matches()
				|| Span.SPAN_NOT_SAMPLED.equals(sampledHeader(request));
		if (log.isDebugEnabled()) {
			log.debug("Received a request to uri [" + uri + "] that should not be sampled [" + skip + "]");
		}
		Optional<Span> spanFromAttribute = getSpanFromAttribute(exchange);
		spanFromAttribute.ifPresent(span -> continueSpan(exchange, span));
		String name = HTTP_COMPONENT + ":" + uri;
		Span span = createSpan(request, exchange, skip, spanFromAttribute, name);
		return chain.filter(exchange).compose(f -> f.doOnSuccess(t -> {
			addResponseTags(response, null);
		}).doOnError(t -> {
			errorParser().parseErrorTags(tracer().getCurrentSpan(), t);
			addResponseTags(response, t);
		}).doFinally(t -> detachOrCloseSpans(span)));
	}

	private String sampledHeader(ServerHttpRequest request) {
		return getHeader(request, Span.SAMPLED_NAME);
	}

	private void continueSpan(ServerWebExchange exchange, Span spanFromRequest) {
		tracer().continueSpan(spanFromRequest);
		exchange.getAttributes().put(TraceRequestAttributes.SPAN_CONTINUED_REQUEST_ATTR, "true");
		if (log.isDebugEnabled()) {
			log.debug("There has already been a span in the request " + spanFromRequest);
		}
	}

	/**
	 * Creates a span and appends it as the current request's attribute
	 */
	private Span createSpan(ServerHttpRequest request, ServerWebExchange exchange,
			boolean skip, Optional<Span> spanFromAttribute, String name) {
		Span spanFromRequest = null;
		if (spanFromAttribute.isPresent()) {
			if (log.isDebugEnabled()) {
				log.debug("Span has already been created - continuing with the previous one");
			}
			return spanFromAttribute.get();
		}
		Span parent = spanExtractor().joinTrace(new ServerHttpRequestTextMap(request));
		if (parent != null) {
			if (log.isDebugEnabled()) {
				log.debug("Found a parent span " + parent + " in the request");
			}
			addRequestTagsForParentSpan(request, parent);
			spanFromRequest = parent;
			tracer().continueSpan(spanFromRequest);
			if (parent.isRemote()) {
				parent.logEvent(Span.SERVER_RECV);
			}
			exchange.getAttributes().put(TRACE_REQUEST_ATTR, spanFromRequest);
			if (log.isDebugEnabled()) {
				log.debug("Parent span is " + parent + "");
			}
		} else {
			if (skip) {
				spanFromRequest = tracer().createSpan(name, NeverSampler.INSTANCE);
			}
			else {
				String header = getHeader(request, Span.SPAN_FLAGS);
				if (Span.SPAN_SAMPLED.equals(header)) {
					spanFromRequest = tracer().createSpan(name, new AlwaysSampler());
				} else {
					spanFromRequest = tracer().createSpan(name);
				}
			}
			spanFromRequest.logEvent(Span.SERVER_RECV);
			exchange.getAttributes().put(TRACE_REQUEST_ATTR, spanFromRequest);
			if (log.isDebugEnabled()) {
				log.debug("No parent span present - creating a new span");
			}
		}
		return spanFromRequest;
	}

	private String getHeader(ServerHttpRequest request, String headerName) {
		List<String> list = request.getHeaders().get(headerName);
		return list == null ? "" : list.isEmpty() ? "" : list.get(0);
	}

	/** Override to add annotations not defined in {@link TraceKeys}. */
	protected void addRequestTags(Span span, ServerHttpRequest request) {
		keysInjector().addRequestTags(span, request.getURI(), request.getMethod().toString());
		for (String name : traceKeys().getHttp().getHeaders()) {
			List<String> values = request.getHeaders().get(name);
			if (values != null && !values.isEmpty()) {
				String key = traceKeys().getHttp().getPrefix() + name.toLowerCase();
				String value = values.size() == 1 ? values.get(0)
						: StringUtils.collectionToDelimitedString(values, ",", "'", "'");
				keysInjector().tagSpan(span, key, value);
			}
		}
	}

	/** Override to add annotations not defined in {@link TraceKeys}. */
	protected void addResponseTags(ServerHttpResponse response, Throwable e) {
		HttpStatus httpStatus = response.getStatusCode();
		if (httpStatus != null && httpStatus == HttpStatus.OK && e != null) {
			// Filter chain threw exception but the response status may not have been set
			// yet, so we have to guess.
			tracer().addTag(traceKeys().getHttp().getStatusCode(),
					String.valueOf(HttpStatus.INTERNAL_SERVER_ERROR.value()));
		}
		// only tag valid http statuses
		else if (httpStatus != null &&
				(httpStatus.value() >= 100 && (httpStatus.value() < 200) || (httpStatus.value() > 399))) {
			tracer().addTag(traceKeys().getHttp().getStatusCode(),
					String.valueOf(response.getStatusCode().value()));
		}
	}

	/**
	 * In order not to send unnecessary data we're not adding request tags to the server
	 * side spans. All the tags are there on the client side.
	 */
	private void addRequestTagsForParentSpan(ServerHttpRequest request, Span spanFromRequest) {
		if (spanFromRequest.getName().contains("parent")) {
			addRequestTags(spanFromRequest, request);
		}
	}

	private Optional<Span> getSpanFromAttribute(ServerWebExchange exchange) {
		Optional<Span> attribute = exchange.getAttribute(TRACE_REQUEST_ATTR);
		return attribute == null ? Optional.ofNullable(null) : attribute;
	}

	private void detachOrCloseSpans(Span spanFromRequest) {
		Span span = spanFromRequest;
		if (span != null) {
			if (span.hasSavedSpan()) {
				recordParentSpan(span.getSavedSpan());
			}
			recordParentSpan(span);
			tracer().close(span);
		}
	}

	private void recordParentSpan(Span parent) {
		if (parent == null) {
			return;
		}
		if (parent.isRemote()) {
			if (log.isDebugEnabled()) {
				log.debug("Trying to send the parent span " + parent + " to Zipkin");
			}
			parent.stop();
			// should be already done by HttpServletResponse wrappers
			SsLogSetter.annotateWithServerSendIfLogIsNotAlreadyPresent(parent);
			spanReporter().report(parent);
		} else {
			// should be already done by HttpServletResponse wrappers
			SsLogSetter.annotateWithServerSendIfLogIsNotAlreadyPresent(parent);
		}
	}

	Tracer tracer() {
		if (this.tracer == null) {
			this.tracer = this.beanFactory.getBean(Tracer.class);
		}
		return this.tracer;
	}

	TraceKeys traceKeys() {
		if (this.traceKeys == null) {
			this.traceKeys = this.beanFactory.getBean(TraceKeys.class);
		}
		return this.traceKeys;
	}

	SpanReporter spanReporter() {
		if (this.spanReporter == null) {
			this.spanReporter = this.beanFactory.getBean(SpanReporter.class);
		}
		return this.spanReporter;
	}

	HttpSpanExtractor spanExtractor() {
		if (this.spanExtractor == null) {
			this.spanExtractor = this.beanFactory.getBean(HttpSpanExtractor.class);
		}
		return this.spanExtractor;
	}

	HttpTraceKeysInjector keysInjector() {
		if (this.httpTraceKeysInjector == null) {
			this.httpTraceKeysInjector = this.beanFactory.getBean(HttpTraceKeysInjector.class);
		}
		return this.httpTraceKeysInjector;
	}

	ErrorParser errorParser() {
		if (this.errorParser == null) {
			this.errorParser = this.beanFactory.getBean(ErrorParser.class);
		}
		return this.errorParser;
	}
}
