package org.springframework.cloud.sleuth.zipkin.zuul;

import com.netflix.zuul.ZuulFilter;
import com.netflix.zuul.context.RequestContext;
import lombok.extern.apachecommons.CommonsLog;
import org.springframework.cloud.sleuth.zipkin.ZipkinInterceptor;

import javax.servlet.http.HttpServletRequest;

/**
 * @author Spencer Gibb
 */
@CommonsLog
public class ZipkinPostFilter extends ZuulFilter {

    private ZipkinInterceptor zipkinInterceptor;

    public ZipkinPostFilter(ZipkinInterceptor zipkinInterceptor) {
        this.zipkinInterceptor = zipkinInterceptor;
    }

    @Override
    public String filterType() {
        return "post";
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

        zipkinInterceptor.postTrace(request);

        return null;
    }
}
