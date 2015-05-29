package org.springframework.cloud.sleuth.zipkin;

import com.github.kristofa.brave.BraveHttpHeaders;
import com.github.kristofa.brave.ClientRequestAdapter;
import com.github.kristofa.brave.ClientResponseAdapter;
import com.github.kristofa.brave.client.ClientRequestInterceptor;
import com.github.kristofa.brave.client.ClientResponseInterceptor;
import com.google.common.base.Optional;
import lombok.SneakyThrows;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.net.URI;

/**
 * @author Spencer Gibb
 */
public class ZipkinRestTemplateInterceptor implements ClientHttpRequestInterceptor {

    private final ClientRequestInterceptor clientRequestInterceptor;
    private final ClientResponseInterceptor clientResponseInterceptor;

    public ZipkinRestTemplateInterceptor(ClientRequestInterceptor clientRequestInterceptor, ClientResponseInterceptor clientResponseInterceptor) {
        this.clientRequestInterceptor = clientRequestInterceptor;
        //TODO: ClientResponseInterceptor assumes >= 300 is error
        this.clientResponseInterceptor = clientResponseInterceptor;
    }

    @SneakyThrows
    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {

        RequestAdapter requestAdapter = new RequestAdapter(request);
        clientRequestInterceptor.handle(requestAdapter, Optional.<String>absent());

        ClientHttpResponse response = null;
        Exception exception = null;
        try {
            response = execution.execute(request, body);
        } catch (final Exception e) {
            exception = e;
        }

        clientResponseInterceptor.handle(new ResponseAdapter(response));
        if(exception != null) {
            throw exception;
        }
        return response;
    }

    class RequestAdapter implements ClientRequestAdapter {

        HttpRequest request;

        public RequestAdapter(HttpRequest request) {
            this.request = request;
        }

        @Override
        public URI getUri() {
            return request.getURI();
        }

        @Override
        public String getMethod() {
            return request.getMethod().toString();
        }

        @Override
        public Optional<String> getSpanName() {
            String spanNameHeader = request.getHeaders().getFirst(BraveHttpHeaders.SpanName.getName());
            return Optional.fromNullable(spanNameHeader);
        }

        @Override
        public void addHeader(String header, String value) {
            request.getHeaders().add(header, value);
        }
    }

    class ResponseAdapter implements ClientResponseAdapter {
        ClientHttpResponse response;

        public ResponseAdapter(ClientHttpResponse response) {
            this.response = response;
        }

        @SneakyThrows
        @Override
        public int getStatusCode() {
            if (response == null) {
                return 0;
            }
            return response.getRawStatusCode();
        }
    }
}
