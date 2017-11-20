/**
 * TODO License
 */

package org.springframework.cloud.sleuth.instrument.db;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.sleuth.ErrorParser;
import org.springframework.cloud.sleuth.SpanNamer;
import org.springframework.cloud.sleuth.TraceKeys;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.autoconfig.TraceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * TODO Docs
 */
@Configuration
@ConditionalOnProperty(value = "spring.sleuth.db.enabled", matchIfMissing = true)
@AutoConfigureAfter(TraceAutoConfiguration.class)
@ConditionalOnBean(Tracer.class)
public class TraceDbAutoConfiguration {

	@Bean
	public TraceDbAspect traceDbAspect(Tracer tracer, TraceKeys traceKeys,
			SpanNamer spanNamer, ErrorParser errorParser) {
		return new TraceDbAspect(tracer, traceKeys, spanNamer, errorParser);
	}
}
