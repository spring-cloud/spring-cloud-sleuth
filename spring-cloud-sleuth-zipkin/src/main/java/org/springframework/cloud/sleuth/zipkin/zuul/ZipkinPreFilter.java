package org.springframework.cloud.sleuth.zipkin.zuul;

import javax.servlet.http.HttpServletRequest;

import lombok.extern.apachecommons.CommonsLog;

import org.springframework.cloud.sleuth.zipkin.ZipkinInterceptor;

import com.netflix.zuul.ZuulFilter;
import com.netflix.zuul.context.RequestContext;

/**
 * @author Spencer Gibb
 */
@CommonsLog
public class ZipkinPreFilter extends ZuulFilter {

    private ZipkinInterceptor zipkinInterceptor;

    public ZipkinPreFilter(ZipkinInterceptor zipkinInterceptor) {
        this.zipkinInterceptor = zipkinInterceptor;
    }

    @Override
    public String filterType() {
        return "pre";
    }

    @Override
    public int filterOrder() {
        return 0;
    }

    @Override
    public boolean shouldFilter() {
        return true;
    }

    @Override
    public Object run() {
        HttpServletRequest request = RequestContext.getCurrentContext().getRequest();

        zipkinInterceptor.preTrace(request);

        return null;
    }
}
