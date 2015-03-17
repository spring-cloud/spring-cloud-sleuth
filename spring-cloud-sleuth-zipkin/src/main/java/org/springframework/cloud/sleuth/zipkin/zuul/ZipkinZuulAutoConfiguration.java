package org.springframework.cloud.sleuth.zipkin.zuul;

import com.netflix.zuul.ZuulFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.cloud.sleuth.zipkin.ZipkinInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Spencer Gibb
 */
@Configuration
@ConditionalOnClass(ZuulFilter.class)
@ConditionalOnBean(ZipkinInterceptor.class)
public class ZipkinZuulAutoConfiguration {

    @Bean
    public ZipkinPreFilter zipkinPreFilter(ZipkinInterceptor zipkinInterceptor) {
        return new ZipkinPreFilter(zipkinInterceptor);
    }

    @Bean
    public ZipkinPostFilter zipkinPostFilter(ZipkinInterceptor zipkinInterceptor) {
        return new ZipkinPostFilter(zipkinInterceptor);
    }
}
