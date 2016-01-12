package org.springframework.cloud.sleuth.zipkin.stream;

import io.zipkin.Sampler;
import java.io.UnsupportedEncodingException;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.Cloud;
import org.springframework.cloud.CloudFactory;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.TimelineAnnotation;
import org.springframework.cloud.sleuth.stream.Host;
import org.springframework.cloud.sleuth.stream.SleuthSink;
import org.springframework.cloud.sleuth.stream.Spans;
import org.springframework.cloud.sleuth.zipkin.stream.ZipkinMessageListener.NotSleuthStreamClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.util.StringUtils;

import io.zipkin.Annotation;
import io.zipkin.BinaryAnnotation;
import io.zipkin.BinaryAnnotation.Type;
import io.zipkin.Constants;
import io.zipkin.Endpoint;
import io.zipkin.Span.Builder;
import io.zipkin.SpanStore;
import lombok.extern.apachecommons.CommonsLog;

@MessageEndpoint
@CommonsLog
@Conditional(NotSleuthStreamClient.class)
public class ZipkinMessageListener {

	@Autowired
	SpanStore spanStore;

	@Autowired
	Sampler sampler;

	@ServiceActivator(inputChannel = SleuthSink.INPUT)
	public void sink(Spans input) {
		Iterator<io.zipkin.Span> sampled = new SamplingZipkinSpanIterator(sampler, input);
		if (sampled.hasNext()) {
			this.spanStore.accept(sampled);
		}
	}

	/**
	 * Converts a given Sleuth span to a Zipkin Span.
	 * <ul>
	 * <li>Set ids, etc
	 * <li>Create timeline annotations based on data from Span object.
	 * <li>Create binary annotations based on data from Span object.
	 * </ul>
	 */
	// VisibleForTesting
	static io.zipkin.Span convert(Span span, Host host) {
		Builder zipkinSpan = new io.zipkin.Span.Builder();

		Endpoint ep = Endpoint.create(host.getServiceName(), host.getIpv4(),
				host.getPort().shortValue());

		// A zipkin span without any annotations cannot be queried, add special "lc" to avoid that.
		if (span.getTimelineAnnotations().isEmpty() && span.getAnnotations().isEmpty()) {
			// TODO: javadocs say this isn't nullable!
			String processId = span.getProcessId() != null
					? span.getProcessId().toLowerCase()
					: "unknown";
			zipkinSpan.addBinaryAnnotation(
					BinaryAnnotation.create(Constants.LOCAL_COMPONENT, processId, ep)
			);
		} else {
			addZipkinAnnotations(zipkinSpan, span, ep);
			addZipkinBinaryAnnotations(zipkinSpan, span, ep);
		}

		zipkinSpan.timestamp(span.getBegin() * 1000);
		zipkinSpan.duration((span.getEnd() - span.getBegin()) * 1000);
		zipkinSpan.traceId(hash(span.getTraceId()));
		if (span.getParents().size() > 0) {
			if (span.getParents().size() > 1) {
				log.error("zipkin doesn't support spans with multiple parents.  Omitting "
						+ "other parents for " + span);
			}
			zipkinSpan.parentId(hash(span.getParents().get(0)));
		}
		zipkinSpan.id(hash(span.getSpanId()));
		if (StringUtils.hasText(span.getName())) {
			zipkinSpan.name(span.getName());
		}
		return zipkinSpan.build();
	}

	/**
	 * Add annotations from the sleuth Span.
	 */
	private static void addZipkinAnnotations(Builder zipkinSpan, Span span, Endpoint endpoint) {
		for (TimelineAnnotation ta : span.getTimelineAnnotations()) {
			Annotation zipkinAnnotation = new Annotation.Builder()
					.endpoint(endpoint)
					.timestamp(ta.getTime() * 1000) // Zipkin is in microseconds
					.value(ta.getMsg())
					.build();
			zipkinSpan.addAnnotation(zipkinAnnotation);
		}
	}

	/**
	 * Creates a list of Annotations that are present in sleuth Span object.
	 *
	 * @return list of Annotations that could be added to Zipkin Span.
	 */
	private static void addZipkinBinaryAnnotations(Builder zipkinSpan, Span span,
			Endpoint endpoint) {
		for (Map.Entry<String, String> e : span.getAnnotations().entrySet()) {
			BinaryAnnotation.Builder binaryAnn = new BinaryAnnotation.Builder();
			binaryAnn.type(Type.STRING);
			binaryAnn.key(e.getKey());
			try {
				binaryAnn.value(e.getValue().getBytes("UTF-8"));
			}
			catch (UnsupportedEncodingException ex) {
				log.error("Error encoding string as UTF-8", ex);
			}
			binaryAnn.endpoint(endpoint);
			zipkinSpan.addBinaryAnnotation(binaryAnn.build());
		}
	}

	private static long hash(String string) {
		long h = 1125899906842597L;
		if (string == null) {
			return h;
		}
		int len = string.length();

		for (int i = 0; i < len; i++) {
			h = 31 * h + string.charAt(i);
		}
		return h;
	}

	protected static class NotSleuthStreamClient extends SpringBootCondition {

		@Override
		public ConditionOutcome getMatchOutcome(ConditionContext context,
				AnnotatedTypeMetadata metadata) {
			Environment environment = context.getEnvironment();
			if ("true".equals(environment
					.resolvePlaceholders("${spring.sleuth.stream.enabled:}"))) {
				return ConditionOutcome
						.noMatch("Found spring.sleuth.stream.enabled=true");
			}
			if (environment instanceof ConfigurableEnvironment) {
				ConfigurableEnvironment configurable = (ConfigurableEnvironment) environment;
				configurable.getPropertySources()
						.addLast(
								new MapPropertySource("spring.sleuth.stream",
										Collections.<String, Object>singletonMap(
												"spring.sleuth.stream.enabled",
												"false")));
			}
			return ConditionOutcome.match("Not found: spring.sleuth.stream.enabled");
		}

	}

	@Configuration
	@Profile("cloud")
	protected static class CloudDataSourceConfiguration {

		@Bean
		public Cloud cloud() {
			return new CloudFactory().getCloud();
		}

		@Value("${zipkin.collector.sample-rate:1.0}")
		float sampleRate = 1.0f;

		@Bean
		Sampler traceIdSampler() {
			return Sampler.create(this.sampleRate);
		}

		@Bean
		@ConfigurationProperties(DataSourceProperties.PREFIX)
		public DataSource dataSource() {
			return cloud().getSingletonServiceConnector(DataSource.class, null);
		}

	}

}
