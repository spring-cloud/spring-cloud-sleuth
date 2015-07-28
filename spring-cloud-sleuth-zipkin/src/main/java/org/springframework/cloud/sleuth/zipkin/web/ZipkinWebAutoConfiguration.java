package org.springframework.cloud.sleuth.zipkin.web;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.cloud.sleuth.autoconfig.TraceAutoConfiguration;
import org.springframework.cloud.sleuth.zipkin.ZipkinAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.kristofa.brave.EndPointSubmitter;
import com.github.kristofa.brave.ServerTracerConfig;

/**
 * @author Spencer Gibb
 */
@Configuration
@ConditionalOnClass(ServerTracerConfig.class)
@ConditionalOnWebApplication
@ConditionalOnProperty(value = "spring.cloud.sleuth.zipkin.enabled", matchIfMissing = true)
@AutoConfigureAfter(ZipkinAutoConfiguration.class)
@AutoConfigureBefore(TraceAutoConfiguration.class)
public class ZipkinWebAutoConfiguration {

	@Autowired
	private EndPointSubmitter endPointSubmitter;

	@Bean
	public ZipkinFilter zipkinFilter() {
		return new ZipkinFilter(this.endPointSubmitter);
	}

}
