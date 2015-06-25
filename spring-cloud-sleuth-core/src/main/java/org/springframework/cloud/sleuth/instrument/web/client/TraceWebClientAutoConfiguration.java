package org.springframework.cloud.sleuth.instrument.web.client;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;

/**
 * @author Spencer Gibb
 */
@Configuration
@ConditionalOnProperty(value = "spring.cloud.sleuth.trace.web.client.enabled", matchIfMissing = true)
@ConditionalOnClass(RestTemplate.class)
public class TraceWebClientAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public TraceRestTemplateInterceptor traceRestTemplateInterceptor() {
		return new TraceRestTemplateInterceptor();
	}

	@Bean
	@ConditionalOnMissingBean
	public RestTemplate restTemplate() {
		return new RestTemplate();
	}

	@Configuration
	protected static class TraceInterceptorConfiguration {

		@Autowired(required = false)
		private RestTemplate restTemplate;

		@Autowired
		private TraceRestTemplateInterceptor traceRestTemplateInterceptor;

		@PostConstruct
		public void init() {
			if (restTemplate != null) {
				restTemplate.getInterceptors().add(traceRestTemplateInterceptor);
			}
		}
	}
}
