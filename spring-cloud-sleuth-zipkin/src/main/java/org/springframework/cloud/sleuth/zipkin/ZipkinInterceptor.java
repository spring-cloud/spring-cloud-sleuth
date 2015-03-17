package org.springframework.cloud.sleuth.zipkin;

import com.github.kristofa.brave.EndPointSubmitter;
import com.github.kristofa.brave.ServerTracer;
import lombok.extern.apachecommons.CommonsLog;

/**
 * @author Spencer Gibb
 */
@CommonsLog
public abstract class ZipkinInterceptor<T> {

    private final ServerTracer serverTracer;
    private final EndPointSubmitter endPointSubmitter;

    protected ZipkinInterceptor(ServerTracer serverTracer, EndPointSubmitter endPointSubmitter) {
        this.serverTracer = serverTracer;
        this.endPointSubmitter = endPointSubmitter;
    }

    public void preTrace(T context) {
        submitEndpoint(context, endPointSubmitter);

        final TraceData traceData = getTraceData(context);
        serverTracer.clearCurrentSpan();

        if (Boolean.FALSE.equals(traceData.getShouldBeSampled())) {
            serverTracer.setStateNoTracing();
            log.debug("Received indication that we should NOT trace.");
        } else {
            final String spanName = getSpanName(context, traceData);
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
    }

    protected abstract void submitEndpoint(T context, EndPointSubmitter endPointSubmitter);
    protected abstract TraceData getTraceData(T context);
    protected abstract String getSpanName(T context, TraceData traceData);

    public void postTrace(T context) {
        // We can submit this in any case. When server state is not set or
        // we should not trace this request nothing will happen.
        log.debug("Sending server send.");
        try {
            serverTracer.setServerSend();
        } finally {
            serverTracer.clearCurrentSpan();
        }
    }

    protected EndPointSubmitter getEndPointSubmitter() {
        return endPointSubmitter;
    }

    protected ServerTracer getServerTracer() {
        return serverTracer;
    }
}
