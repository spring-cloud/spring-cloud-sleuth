package sample;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.sleuth.Trace;
import org.springframework.cloud.sleuth.TraceScope;

import java.util.ArrayDeque;
import java.util.Deque;

public class TraceProcessorFactory {

    public static final String X_TRACE_SCOPES = "X-Sleuth-Trace-Scopes";

    @Autowired
    private Trace trace;

    private static Deque<TraceScope> getTraceScopes(Exchange exchange) {
        @SuppressWarnings("unchecked")
        Deque<TraceScope> traceScopes = (Deque<TraceScope>) exchange.getIn().getHeader(X_TRACE_SCOPES);

        if (traceScopes == null) {
            traceScopes = new ArrayDeque<>();
        }
        return traceScopes;
    }

    Processor startSpanProcessor(final String spanName) {
        return new Processor() {
            @Override
            public void process(Exchange exchange) {
                Deque<TraceScope> traceScopes = getTraceScopes(exchange);

                traceScopes.push(trace.startSpan(spanName));

                exchange.getOut().setHeader(X_TRACE_SCOPES, traceScopes);
            }
        };
    }

    Processor closeSpanProcessor() {
        return new Processor() {
            @Override
            public void process(Exchange exchange) {
                getTraceScopes(exchange).pop().close();
            }
        };
    }

    Processor closeSpansProcessor() {
        return new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                Deque<TraceScope> traceScopes = getTraceScopes(exchange);
                while (!traceScopes.isEmpty()) {
                    traceScopes.pop().close();
                }
            }
        };
    }
}
