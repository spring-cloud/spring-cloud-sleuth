package org.springframework.cloud.sleuth.zipkin.web;

import org.springframework.cloud.sleuth.zipkin.ZipkinInterceptor;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import java.io.IOException;

/**
 * @author Spencer Gibb
 */
public class ZipkinFilter implements Filter {

    private final ZipkinInterceptor zipkinInterceptor;

    public ZipkinFilter(ZipkinInterceptor zipkinInterceptor) {
        this.zipkinInterceptor = zipkinInterceptor;
    }


    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        //NOOP
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        zipkinInterceptor.preTrace(request);

        chain.doFilter(request, response);

        zipkinInterceptor.postTrace(request);
    }

    @Override
    public void destroy() {
        //NOOP
    }
}
