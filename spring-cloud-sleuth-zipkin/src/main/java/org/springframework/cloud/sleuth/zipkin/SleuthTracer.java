package org.springframework.cloud.sleuth.zipkin;

import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lombok.extern.apachecommons.CommonsLog;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.cloud.sleuth.SpanIdentifiers;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.TimelineAnnotation;
import org.springframework.cloud.sleuth.event.SpanStoppedEvent;
import org.springframework.context.event.EventListener;

import com.github.kristofa.brave.SpanCollector;
import com.twitter.zipkin.gen.Annotation;
import com.twitter.zipkin.gen.AnnotationType;
import com.twitter.zipkin.gen.BinaryAnnotation;
import com.twitter.zipkin.gen.Endpoint;
import com.twitter.zipkin.gen.zipkinCoreConstants;

/**
 * @author Spencer Gibb
 */
@CommonsLog
public class SleuthTracer {

	private SpanCollector spanCollector;
	@Value("${spring.application.name:application}")
	private String appName;
	@Autowired
	private ServerProperties serverProperties;

	public SleuthTracer(SpanCollector spanCollector) {
		this.spanCollector = spanCollector;
	}

	@EventListener
	public void start(SpanStoppedEvent event) {
		this.spanCollector.collect(convert(event.getSpan()));
	}

	/**
	 * Converts a given Sleuth span to a Zipkin Span.
	 * <ul>
	 * <li>First set the start annotation. [CS, SR], depending whether it is a client service or not.
	 * <li>Set other id's, etc [TraceId's etc]
	 * <li>Create binary annotations based on data from HTrace Span object.
	 * <li>Set the last annotation. [SS, CR]
	 * </ul>
	 */
	public com.twitter.zipkin.gen.Span convert(Span span) {
		com.twitter.zipkin.gen.Span zipkinSpan = new com.twitter.zipkin.gen.Span();

		String serviceName = getServiceName(span);
		int address = getAddress();
		Integer port = getPort();

		Endpoint ep = new Endpoint(address, port.shortValue(), serviceName);
		List<Annotation> annotationList = createZipkinAnnotations(span, ep);
		List<BinaryAnnotation> binaryAnnotationList = createZipkinBinaryAnnotations(span, ep);
		zipkinSpan.setTrace_id(hash(span.getTraceId()));
		if (span.getParents().size() > 0) {
			if (span.getParents().size() > 1) {
				log.error("zipkin doesn't support spans with multiple parents.  Omitting " +
						"other parents for " + span);
			}
			zipkinSpan.setParent_id(hash(span.getParents().get(0)));
		}
		zipkinSpan.setId(hash(span.getSpanId()));
		zipkinSpan.setName(span.getName());
		zipkinSpan.setAnnotations(annotationList);
		zipkinSpan.setBinary_annotations(binaryAnnotationList);
		return zipkinSpan;
	}

	public Integer getPort() {
		Integer port;
		if (this.serverProperties.getPort() != null) {
			port = this.serverProperties.getPort();
		} else {
			port = 8080; //TODO: support random port
		}
		return port;
	}

	public int getAddress() {
		String address;
		if (this.serverProperties.getAddress() != null) {
			address = this.serverProperties.getAddress().getHostAddress();
		} else {
			address = "127.0.0.1"; //TODO: get address from config
		}
		return ipAddressToInt(address);
	}

	public String getServiceName(SpanIdentifiers span) {
		String serviceName;
		if (span.getProcessId() != null) {
			serviceName = span.getProcessId().toLowerCase();
		} else {
			serviceName = this.appName;
		}
		return serviceName;
	}


	private int ipAddressToInt(final String ip) {
		InetAddress inetAddress = null;
		try {
			inetAddress = InetAddress.getByName(ip);
		} catch (final UnknownHostException e) {
			throw new IllegalArgumentException(e);
		}
		return ByteBuffer.wrap(inetAddress.getAddress()).getInt();
	}

	/**
	 * Add annotations from the sleuth Span.
	 */
	private List<Annotation> createZipkinAnnotations(Span span,
			Endpoint endpoint) {
		List<Annotation> annotationList = new ArrayList<>();

		int duration = (int)(span.getEnd() - span.getBegin());

		// add first zipkin  annotation.
		annotationList.add(createZipkinAnnotation(zipkinCoreConstants.CLIENT_SEND, span.getBegin(), 0, endpoint, true));
		annotationList.add(createZipkinAnnotation(zipkinCoreConstants.SERVER_RECV, span.getBegin(), 0, endpoint, true));
		// add sleuth time annotation
		for (TimelineAnnotation ta : span.getTimelineAnnotations()) {
			annotationList.add(createZipkinAnnotation(ta.getMsg(), ta.getTime(), 0, endpoint, true));
		}
		// add last zipkin annotation
		annotationList.add(createZipkinAnnotation(zipkinCoreConstants.SERVER_SEND, span.getEnd(), duration, endpoint, false));
		annotationList.add(createZipkinAnnotation(zipkinCoreConstants.CLIENT_RECV, span.getEnd(), duration, endpoint, false));
		return annotationList;
	}

	/**
	 * Creates a list of Annotations that are present in sleuth Span object.
	 *
	 * @return list of Annotations that could be added to Zipkin Span.
	 */
	private List<BinaryAnnotation> createZipkinBinaryAnnotations(Span span,
			Endpoint endpoint) {
		List<BinaryAnnotation> l = new ArrayList<>();
		for (Map.Entry<String, String> e : span.getKVAnnotations().entrySet()) {
			BinaryAnnotation binaryAnn = new BinaryAnnotation();
			binaryAnn.setAnnotation_type(AnnotationType.BYTES);
			binaryAnn.setKey(e.getKey());
			try {
				binaryAnn.setValue(e.getValue().getBytes("UTF-8"));
			} catch (UnsupportedEncodingException ex) {
				log.error("Error encoding string as UTF-8", ex);
			}
			binaryAnn.setHost(endpoint);
			l.add(binaryAnn);
		}
		return l;
	}

	/**
	 * Create an annotation with the correct times and endpoint.
	 *
	 * @param value       Annotation value
	 * @param time        timestamp will be extracted
	 * @param endpoint    the endpoint this annotation will be associated with.
	 * @param sendRequest use the first or last timestamp.
	 */
	private static Annotation createZipkinAnnotation(String value, long time, int duration,
			Endpoint endpoint, boolean sendRequest) {
		Annotation annotation = new Annotation();
		annotation.setHost(endpoint);

		// Zipkin is in microseconds
		if (sendRequest) {
			annotation.setTimestamp(time * 1000);
		} else {
			annotation.setTimestamp(time * 1000);
		}

		if (duration > 0) {
			annotation.setDuration(duration * 1000);
		}
		annotation.setValue(value);
		return annotation;
	}

	private static long hash(String string) {
		long h = 1125899906842597L;
		int len = string.length();

		for (int i = 0; i < len; i++) {
			h = 31 * h + string.charAt(i);
		}
		return h;
	}

}
