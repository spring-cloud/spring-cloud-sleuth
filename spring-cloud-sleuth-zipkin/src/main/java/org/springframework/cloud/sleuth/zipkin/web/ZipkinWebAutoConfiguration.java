package org.springframework.cloud.sleuth.zipkin.web;

import com.github.kristofa.brave.EndPointSubmitter;
import com.github.kristofa.brave.ServerTracer;
import com.github.kristofa.brave.ServerTracerConfig;
import com.github.kristofa.brave.client.ClientRequestInterceptor;
import com.github.kristofa.brave.client.ClientResponseInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.cloud.sleuth.zipkin.ZipkinAutoConfiguration;
import org.springframework.cloud.sleuth.zipkin.ZipkinRestTemplateInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;

/**
 * @author Spencer Gibb
 */
@Configuration
@ConditionalOnClass(ServerTracerConfig.class)
@ConditionalOnWebApplication
@ConditionalOnProperty(value = "spring.cloud.sleuth.zipkin.enabled", matchIfMissing = true)
@AutoConfigureAfter(ZipkinAutoConfiguration.class)
public class ZipkinWebAutoConfiguration {

	@Autowired
	private EndPointSubmitter endPointSubmitter;

	@Autowired
	private ServerTracer serverTracer;

	@Bean
	public ZipkinHandlerInterceptor zipkinHandlerInterceptor() {
		return new ZipkinHandlerInterceptor(httpServletRequestInterceptor());
	}

    @Bean
    public ZipkinFilter zipkinFilter() {
        return new ZipkinFilter(httpServletRequestInterceptor());
    }

    @Bean
    public HttpServletRequestInterceptor httpServletRequestInterceptor() {
        return new HttpServletRequestInterceptor(serverTracer, endPointSubmitter);
    }

	@Bean
    @ConditionalOnBean(ZipkinHandlerInterceptor.class)
	public WebMvcConfigurerAdapter webMvcConfigurerAdapter(ZipkinHandlerInterceptor zipkinHandlerInterceptor) {
		return new ZipkinWebConfigurer(zipkinHandlerInterceptor);
	}

    @Configuration
    protected static class RestTemplateConfig {

        @Autowired
        private ClientRequestInterceptor clientRequestInterceptor;

        @Autowired
        private ClientResponseInterceptor clientResponseInterceptor;

        @Bean
        public ZipkinRestTemplateInterceptor zipkinRestTemplateInterceptor() {
            return new ZipkinRestTemplateInterceptor(clientRequestInterceptor,
                    clientResponseInterceptor);
        }

    }

	protected static class ZipkinWebConfigurer extends WebMvcConfigurerAdapter {
		private ZipkinHandlerInterceptor interceptor;

		public ZipkinWebConfigurer(ZipkinHandlerInterceptor interceptor) {
			this.interceptor = interceptor;
		}

		@Override
		public void addInterceptors(InterceptorRegistry registry) {
			registry.addInterceptor(interceptor).addPathPatterns("/**");
		}
	}
}
