/*
 * Copyright 2013-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.sleuth.stream;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Properties related to Sleuth Stream
 *
 * @author Dave Syer
 * @since 1.0.0
 */
@ConfigurationProperties("spring.sleuth.stream")
public class SleuthStreamProperties {
	private boolean enabled = true;
	private String group = SleuthSink.INPUT;
	private Poller poller = new Poller();

	public boolean isEnabled() {
		return this.enabled;
	}

	public String getGroup() {
		return this.group;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public void setGroup(String group) {
		this.group = group;
	}

	public Poller getPoller() {
		return this.poller;
	}

	public static class Poller {
		/**
		 * Fixed delay (ms). Default: 1000
		 */
		private long fixedDelay = 1000L;

		/**
		 * Max messages per poll. Default: -1 (unbounded)
		 */
		private int maxMessagesPerPoll = -1;

		public long getFixedDelay() {
			return this.fixedDelay;
		}

		public int getMaxMessagesPerPoll() {
			return this.maxMessagesPerPoll;
		}

		public void setFixedDelay(long fixedDelay) {
			this.fixedDelay = fixedDelay;
		}

		public void setMaxMessagesPerPoll(int maxMessagesPerPoll) {
			this.maxMessagesPerPoll = maxMessagesPerPoll;
		}
	}
}
