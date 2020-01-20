package org.springframework.cloud.sleuth.instrument.web.client.feign;

import java.io.IOException;
import java.util.HashMap;

import brave.Span;
import brave.Tracer;
import brave.http.HttpTracing;
import com.netflix.client.ClientException;
import feign.Client;
import feign.Request;
import feign.Response;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.cloud.loadbalancer.blocking.client.BlockingLoadBalancerClient;
import org.springframework.cloud.openfeign.loadbalancer.FeignBlockingLoadBalancerClient;

/**
 * A trace representation of {@link FeignBlockingLoadBalancerClient}.
 * Needed due to casts in {@link org.springframework.cloud.openfeign.FeignClientFactoryBean}.
 * Based on {@link TraceLoadBalancerFeignClient}.
 *
 * @author Olga Maciaszek-Sharma
 *
 * @since 2.2.0
 * @see TraceLoadBalancerFeignClient
 * @see FeignBlockingLoadBalancerClient
 */
public class TraceFeignBlockingLoadBalancerClient extends FeignBlockingLoadBalancerClient {

	private static final Log LOG = LogFactory
			.getLog(TraceFeignBlockingLoadBalancerClient.class);

	private final BeanFactory beanFactory;

	Tracer tracer;

	HttpTracing httpTracing;

	TracingFeignClient tracingFeignClient;

	TraceFeignBlockingLoadBalancerClient(Client delegate,
			BlockingLoadBalancerClient loadBalancerClient, BeanFactory beanFactory) {
		super(delegate, loadBalancerClient);
		this.beanFactory = beanFactory;
	}

	@Override
	public Response execute(Request request, Request.Options options) throws IOException {
		if (LOG.isDebugEnabled()) {
			LOG.debug("Before send");
		}
		Response response = null;
		Span fallbackSpan = tracer().nextSpan().start();
		try {
			response = super.execute(request, options);
			if (LOG.isDebugEnabled()) {
				LOG.debug("After receive");
			}
			return response;
		}
		catch (Exception e) {
			if (LOG.isDebugEnabled()) {
				LOG.debug("Exception thrown", e);
			}
			if (e instanceof IOException || e.getCause() != null
					&& e.getCause() instanceof ClientException
					&& ((ClientException) e.getCause())
					.getErrorType() == ClientException.ErrorType.GENERAL) {
				if (LOG.isDebugEnabled()) {
					LOG.debug(
							"General exception was thrown, so most likely the traced client wasn't called. Falling back to a manual span");
				}
				fallbackSpan = tracingFeignClient().handleSend(
						new HashMap<>(request.headers()), request, fallbackSpan);
				tracingFeignClient().handleReceive(fallbackSpan, response, e);
			}
			throw e;
		}
		finally {
			fallbackSpan.abandon();
		}
	}

	private Tracer tracer() {
		if (tracer == null) {
			tracer = beanFactory.getBean(Tracer.class);
		}
		return tracer;
	}

	private HttpTracing httpTracing() {
		if (httpTracing == null) {
			httpTracing = beanFactory.getBean(HttpTracing.class);
		}
		return httpTracing;
	}

	private TracingFeignClient tracingFeignClient() {
		if (tracingFeignClient == null) {
			tracingFeignClient = (TracingFeignClient) TracingFeignClient
					.create(httpTracing(), getDelegate());
		}
		return tracingFeignClient;
	}
}
