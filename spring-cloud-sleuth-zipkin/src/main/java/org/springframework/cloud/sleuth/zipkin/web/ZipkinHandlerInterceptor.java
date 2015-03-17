package org.springframework.cloud.sleuth.zipkin.web;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import lombok.extern.apachecommons.CommonsLog;

import org.springframework.cloud.sleuth.zipkin.ZipkinInterceptor;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

/**
 * @author Spencer Gibb
 */
@CommonsLog
public class ZipkinHandlerInterceptor implements HandlerInterceptor {

    private final ZipkinInterceptor zipkinInterceptor;

    public ZipkinHandlerInterceptor(ZipkinInterceptor zipkinInterceptor) {
        this.zipkinInterceptor = zipkinInterceptor;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        zipkinInterceptor.preTrace(request);
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        zipkinInterceptor.postTrace(request);
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
    }

}
