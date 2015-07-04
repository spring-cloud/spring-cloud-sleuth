package org.springframework.cloud.sleuth.instrument.zuul;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.cloud.sleuth.Trace;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.netflix.zuul.ZuulFilter;

/**
 * @author Spencer Gibb
 */
@Configuration
@ConditionalOnClass(ZuulFilter.class)
@ConditionalOnBean(Trace.class)
public class TraceZuulAutoConfiguration {

	@Bean
	public TracePreFilter tracePreFilter(Trace trace) {
		return new TracePreFilter(trace);
	}

	@Bean
	public TracePostFilter tracePostFilter(Trace trace) {
		return new TracePostFilter(trace);
	}
}
