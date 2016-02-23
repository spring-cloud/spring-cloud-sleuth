package org.springframework.cloud.sleuth.metric;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Sleuth related metrics
 *
 * @author Marcin Grzejszczak
 *
 * @since 1.0.0
 */
@ConfigurationProperties("spring.sleuth.metric")
public class SleuthMetricProperties {

	private Span span = new Span();

	public Span getSpan() {
		return this.span;
	}

	public void setSpan(Span span) {
		this.span = span;
	}

	public static class Span {

		private String acceptedName = "counter.span.accepted";

		private String droppedName = "counter.span.dropped";

		public String getAcceptedName() {
			return this.acceptedName;
		}

		public void setAcceptedName(String acceptedName) {
			this.acceptedName = acceptedName;
		}

		public String getDroppedName() {
			return this.droppedName;
		}

		public void setDroppedName(String droppedName) {
			this.droppedName = droppedName;
		}
	}
}
