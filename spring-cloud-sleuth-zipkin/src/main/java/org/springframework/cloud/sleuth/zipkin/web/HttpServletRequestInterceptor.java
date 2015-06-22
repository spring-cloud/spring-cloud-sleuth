package org.springframework.cloud.sleuth.zipkin.web;

import static com.github.kristofa.brave.BraveHttpHeaders.ParentSpanId;
import static com.github.kristofa.brave.BraveHttpHeaders.Sampled;
import static com.github.kristofa.brave.BraveHttpHeaders.SpanId;
import static com.github.kristofa.brave.BraveHttpHeaders.SpanName;
import static com.github.kristofa.brave.BraveHttpHeaders.TraceId;

import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import com.github.kristofa.brave.EndPointSubmitter;
import com.github.kristofa.brave.ServerTracer;
import lombok.extern.apachecommons.CommonsLog;

import org.apache.commons.lang3.StringUtils;
import org.springframework.cloud.sleuth.zipkin.TraceData;
import org.springframework.cloud.sleuth.zipkin.ZipkinInterceptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServletServerHttpRequest;

import com.github.kristofa.brave.IdConversion;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * @author Spencer Gibb
 */
@CommonsLog
public class HttpServletRequestInterceptor extends ZipkinInterceptor<HttpServletRequest> {

    public HttpServletRequestInterceptor(ServerTracer serverTracer, EndPointSubmitter endPointSubmitter) {
        super(serverTracer, endPointSubmitter);
    }

    @Override
    public void submitEndpoint(HttpServletRequest servletRequest) {
        if (!getEndPointSubmitter().endPointSubmitted()) {
            final String localAddr = servletRequest.getLocalAddr();
            final int localPort = servletRequest.getLocalPort();
            final String contextPath = servletRequest.getContextPath();
            log.debug("Setting endpoint: addr: "+localAddr+", port: "+localPort+", contextpath: "+ contextPath);
            getEndPointSubmitter().submit(localAddr, localPort, contextPath);
        }
    }

    @Override
    public TraceData getTraceData(HttpServletRequest request) {
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

    protected Long getFirstLong(final Map.Entry<String, List<String>> headerEntry) {
        final String value = getFirstString(headerEntry);
        if (value == null) {
            return null;
        }
        return IdConversion.convertToLong(value);

    }

    protected Boolean getFirstBoolean(final Map.Entry<String, List<String>> headerEntry) {
        final String firstStringValueFor = getFirstString(headerEntry);
        return firstStringValueFor == null ? null : Boolean.valueOf(firstStringValueFor);
    }

    protected String getFirstString(final Map.Entry<String, List<String>> headerEntry) {
        final List<String> values = headerEntry.getValue();
        if (values != null && values.size() > 0) {
            return headerEntry.getValue().get(0);
        }
        return null;
    }

    @Override
    protected String getSpanName(HttpServletRequest request, TraceData traceData) {
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
}
