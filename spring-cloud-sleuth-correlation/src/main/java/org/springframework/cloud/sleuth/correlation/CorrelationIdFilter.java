/*
 * Copyright 2012-2015 the original author or authors.
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
package org.springframework.cloud.sleuth.correlation;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

import static org.springframework.cloud.sleuth.correlation.CorrelationIdHolder.CORRELATION_ID_HEADER;
import static org.springframework.util.StringUtils.hasText;

/**
 * Filter that takes the value of the {@link CorrelationIdHolder#CORRELATION_ID_HEADER} header
 * from either request or response and sets it in the {@link CorrelationIdHolder}. It also provides
 * that value in {@link MDC} logging related class so that logger prints the value of
 * correlation id at each log.
 *
 * @see CorrelationIdHolder
 * @see MDC
 *
 * @author Jakub Nabrdalik, 4financeIT
 * @author Tomasz Nurkiewicz, 4financeIT
 * @author Marcin Grzejszczak, 4financeIT
 */
public class CorrelationIdFilter extends OncePerRequestFilter {
	private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
	public static final Pattern DEFAULT_SKIP_PATTERN = Pattern.compile("/api-docs.*|/autoconfig|/configprops|/dump|/info|/metrics.*|/mappings|/trace|/swagger.*|.*\\.png|.*\\.css|.*\\.js|.*\\.html");

	private final Pattern skipCorrId;
	private final UuidGenerator uuidGenerator;

	public CorrelationIdFilter() {
		this.uuidGenerator = new UuidGenerator();
		this.skipCorrId = null;
	}

	public CorrelationIdFilter(UuidGenerator uuidGenerator, Pattern skipCorrId) {
		this.uuidGenerator = uuidGenerator;
		this.skipCorrId = skipCorrId;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
		setupCorrelationId(request, response);
		try {
			filterChain.doFilter(request, response);
		} finally {
			cleanupCorrelationId();
		}
	}

	private void setupCorrelationId(HttpServletRequest request, HttpServletResponse response) {
		String correlationIdFromRequest = getCorrelationIdFrom(request);
		String correlationId = (hasText(correlationIdFromRequest)) ? correlationIdFromRequest : getCorrelationIdFrom(response);
		if (!hasText(correlationId) && shouldGenerateCorrId(request)) {
			correlationId = createNewCorrIdIfEmpty();
		}
		CorrelationIdHolder.set(correlationId);
		addCorrelationIdToResponseIfNotPresent(response, correlationId);
	}

	private String getCorrelationIdFrom(final HttpServletResponse response) {
		return withLoggingAs("response", new Callable<String>() {
			@Override
			public String call() throws Exception {
				return response.getHeader(CORRELATION_ID_HEADER);
			}
		});
	}

	private String getCorrelationIdFrom(final HttpServletRequest request) {
		return withLoggingAs("request", new Callable<String>() {
			@Override
			public String call() throws Exception {
				return request.getHeader(CORRELATION_ID_HEADER);
			}
		});
	}

	private String withLoggingAs(String whereWasFound, Callable<String> correlationIdGetter) {
		String correlationId = tryToGetCorrelationId(correlationIdGetter);
		if (hasText(correlationId)) {
			MDC.put(CORRELATION_ID_HEADER, correlationId);
			log.debug("Found correlationId in " + whereWasFound + ": " + correlationId);
		}
		return correlationId;
	}

	private String tryToGetCorrelationId(Callable<String> correlationIdGetter) {
		try {
			return correlationIdGetter.call();
		} catch (Exception e) {
			log.error("Exception occurred while trying to retrieve request header", e);
			return StringUtils.EMPTY;
		}
	}

	private String createNewCorrIdIfEmpty() {
		String currentCorrId = uuidGenerator.create();
		MDC.put(CORRELATION_ID_HEADER, currentCorrId);
		log.debug("Generating new correlationId: " + currentCorrId);
		return currentCorrId;
	}

	protected boolean shouldGenerateCorrId(HttpServletRequest request) {
		final String i = request.getRequestURI();
		final String uri = StringUtils.defaultIfEmpty(i, StringUtils.EMPTY);
		boolean skip = skipCorrId != null && skipCorrId.matcher(uri).matches();
		return !skip;
	}

	private void addCorrelationIdToResponseIfNotPresent(HttpServletResponse response, String correlationId) {
		if (!hasText(response.getHeader(CORRELATION_ID_HEADER))) {
			response.addHeader(CORRELATION_ID_HEADER, correlationId);
		}
	}

	private void cleanupCorrelationId() {
		MDC.remove(CORRELATION_ID_HEADER);
		CorrelationIdHolder.remove();
	}

	@Override
	protected boolean shouldNotFilterAsyncDispatch() {
		return false;
	}
}
