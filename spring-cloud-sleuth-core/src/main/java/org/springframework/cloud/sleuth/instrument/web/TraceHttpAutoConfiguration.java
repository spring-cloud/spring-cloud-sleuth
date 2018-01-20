package org.springframework.cloud.sleuth.instrument.web;

import brave.Tracing;
import brave.http.HttpTracing;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.sleuth.ErrorParser;
import org.springframework.cloud.sleuth.TraceKeys;
import org.springframework.cloud.sleuth.autoconfig.TraceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration Auto-configuration}
 * related to HTTP based communication.
 *
 * @author Marcin Grzejszczak
 * @since 2.0.0
 */
@Configuration
@ConditionalOnBean(Tracing.class)
@ConditionalOnProperty(name = "spring.sleuth.http.enabled", havingValue = "true", matchIfMissing = true)
@AutoConfigureAfter(TraceAutoConfiguration.class)
public class TraceHttpAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(name = "spring.sleuth.http.legacy.enabled", havingValue = "false", matchIfMissing = true)
	HttpTracing sleuthHttpTracing(Tracing tracing) {
		return HttpTracing.create(tracing);
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(name = "spring.sleuth.http.legacy.enabled", havingValue = "true")
	HttpTracing legacySleuthHttpTracing(Tracing tracing, TraceKeys traceKeys, ErrorParser errorParser) {
		return HttpTracing.newBuilder(tracing)
				.clientParser(new SleuthHttpClientParser(traceKeys))
				.serverParser(new SleuthHttpServerParser(traceKeys, errorParser))
				.build();
	}
}
