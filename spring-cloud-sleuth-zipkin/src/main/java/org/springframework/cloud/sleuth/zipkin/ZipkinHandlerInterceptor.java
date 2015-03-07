package org.springframework.cloud.sleuth.zipkin;

import static com.github.kristofa.brave.BraveHttpHeaders.*;

import com.github.kristofa.brave.EndPointSubmitter;
import com.github.kristofa.brave.IdConversion;
import com.github.kristofa.brave.ServerTracer;
import lombok.Data;
import lombok.extern.apachecommons.CommonsLog;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;

/**
 * @author Spencer Gibb
 */
@CommonsLog
public class ZipkinHandlerInterceptor implements HandlerInterceptor {

    private final EndPointSubmitter endPointSubmitter;
    private final ServerTracer serverTracer;

    public ZipkinHandlerInterceptor(EndPointSubmitter endPointSubmitter, ServerTracer serverTracer) {
        this.endPointSubmitter = endPointSubmitter;
        this.serverTracer = serverTracer;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        submitEndpoint(request);

        serverTracer.clearCurrentSpan();
        final TraceData traceData = getTraceData(request);

        if (Boolean.FALSE.equals(traceData.getShouldBeSampled())) {
            serverTracer.setStateNoTracing();
            log.debug("Received indication that we should NOT trace.");
        } else {
            final String spanName = getSpanName(request, traceData);
            if (traceData.getTraceId() != null && traceData.getSpanId() != null) {

                log.debug("Received span information as part of request.");
                serverTracer.setStateCurrentTrace(traceData.getTraceId(), traceData.getSpanId(),
                        traceData.getParentSpanId(), spanName);
            } else {
                log.debug("Received no span state.");
                serverTracer.setStateUnknown(spanName);
            }
            serverTracer.setServerReceived();
        }

        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        // We can submit this in any case. When server state is not set or
        // we should not trace this request nothing will happen.
        log.debug("Sending server send.");
        try {
            serverTracer.setServerSend();
        } finally {
            serverTracer.clearCurrentSpan();
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
    }

    private void submitEndpoint(HttpServletRequest servletRequest) {
        if (!endPointSubmitter.endPointSubmitted()) {
            final String localAddr = servletRequest.getLocalAddr();
            final int localPort = servletRequest.getLocalPort();
            final String contextPath = servletRequest.getContextPath();
            log.debug("Setting endpoint: addr: "+localAddr+", port: "+localPort+", contextpath: "+ contextPath);
            endPointSubmitter.submit(localAddr, localPort, contextPath);
        }
    }


    private TraceData getTraceData(final HttpServletRequest request) {
        ServletServerHttpRequest req = new ServletServerHttpRequest(request);
        HttpHeaders headers = req.getHeaders();

        TraceData traceData = new TraceData();

        for (Map.Entry<String, List<String>> headerEntry : headers.entrySet()) {
            log.debug(headerEntry.getKey() +" = "+ headerEntry.getValue());
            if (TraceId.getName().equalsIgnoreCase(headerEntry.getKey())) {
                traceData.setTraceId(getFirstLong(headerEntry));
            } else if (SpanId.getName().equalsIgnoreCase(headerEntry.getKey())) {
                traceData.setSpanId(getFirstLong(headerEntry));
            } else if (ParentSpanId.getName().equalsIgnoreCase(headerEntry.getKey())) {
                traceData.setParentSpanId(getFirstLong(headerEntry));
            } else if (Sampled.getName().equalsIgnoreCase(headerEntry.getKey())) {
                traceData.setShouldBeSampled(getFirstBoolean(headerEntry));
            } else if (SpanName.getName().equalsIgnoreCase(headerEntry.getKey())) {
                traceData.setSpanName(getFirstString(headerEntry));
            }
        }
        return traceData;
    }

    private String getSpanName(final HttpServletRequest request, final TraceData traceData) {
        if (StringUtils.isNotBlank(traceData.getSpanName())) {
            return traceData.getSpanName();
        } else {
            //TODO: what is the functional equivalent of resteasy request.getPreprocessedPath();
            UriComponents components = UriComponentsBuilder.fromUriString(request.getRequestURL().toString()).build();
            StringBuilder preprocessedPath = new StringBuilder();
            for (String segment : components.getPathSegments()) {
                preprocessedPath.append("/").append(segment);
            }
            if (preprocessedPath.length() == 0) {
                preprocessedPath.append("/");
            }
            return preprocessedPath.toString();
        }
    }

    private Long getFirstLong(final Map.Entry<String, List<String>> headerEntry) {
        final String value = getFirstString(headerEntry);
        if (value == null) {
            return null;
        }
        return IdConversion.convertToLong(value);

    }

    private Boolean getFirstBoolean(final Map.Entry<String, List<String>> headerEntry) {
        final String firstStringValueFor = getFirstString(headerEntry);
        return firstStringValueFor == null ? null : Boolean.valueOf(firstStringValueFor);
    }

    private String getFirstString(final Map.Entry<String, List<String>> headerEntry) {
        final List<String> values = headerEntry.getValue();
        if (values != null && values.size() > 0) {
            return headerEntry.getValue().get(0);
        }
        return null;
    }

    @Data
    class TraceData {
        private Long traceId;
        private Long spanId;
        private Long parentSpanId;
        private Boolean shouldBeSampled;
        private String spanName;
    }
}
