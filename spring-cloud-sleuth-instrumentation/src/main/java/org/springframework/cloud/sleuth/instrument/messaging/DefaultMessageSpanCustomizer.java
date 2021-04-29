/*
 * Copyright 2013-2021 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.messaging;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanCustomizer;
import org.springframework.cloud.sleuth.internal.SpanNameUtil;
import org.springframework.integration.channel.AbstractMessageChannel;
import org.springframework.integration.context.IntegrationObjectSupport;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.util.ClassUtils;

/**
 * Provides default customization of messaging spans.
 *
 * @author Marcin Grzejszczak
 * @since 2.2.7
 */
public class DefaultMessageSpanCustomizer implements MessageSpanCustomizer {

	private final boolean integrationObjectSupportPresent;

	public DefaultMessageSpanCustomizer() {
		this.integrationObjectSupportPresent = ClassUtils
				.isPresent("org.springframework.integration.context.IntegrationObjectSupport", null);
	}

	/**
	 * Retrieves the channel name from the {@link MessageChannel}.
	 * @param channel - message channel from which message got received or was sent to
	 * @return channel name
	 * @since 2.2.7
	 */
	protected String channelName(MessageChannel channel) {
		String name = null;
		if (this.integrationObjectSupportPresent) {
			if (channel instanceof IntegrationObjectSupport) {
				name = ((IntegrationObjectSupport) channel).getComponentName();
			}
			if (name == null && channel instanceof AbstractMessageChannel) {
				name = ((AbstractMessageChannel) channel).getFullChannelName();
			}
		}
		if (name == null) {
			return channel.toString();
		}
		return name;
	}

	private String messageChannelName(MessageChannel channel) {
		return SpanNameUtil.shorten(channelName(channel));
	}

	@Override
	public Span customizeHandle(Span spanCustomizer, Message<?> message, @Nullable MessageChannel messageChannel) {
		spanCustomizer.name("handle");
		addTags(spanCustomizer, messageChannel);
		return spanCustomizer;
	}

	@Override
	public Span.Builder customizeHandle(Span.Builder spanCustomizer, Message<?> message,
			@Nullable MessageChannel messageChannel) {
		spanCustomizer.name("handle");
		addTags(spanCustomizer, messageChannel);
		return spanCustomizer;
	}

	@Override
	public Span.Builder customizeReceive(Span.Builder spanCustomizer, Message<?> message,
			@Nullable MessageChannel messageChannel) {
		spanCustomizer.name("receive");
		addTags(spanCustomizer, messageChannel);
		return spanCustomizer;
	}

	@Override
	public Span.Builder customizeSend(Span.Builder spanCustomizer, Message<?> message,
			@Nullable MessageChannel messageChannel) {
		spanCustomizer.name("send");
		addTags(spanCustomizer, messageChannel);
		return spanCustomizer;
	}

	private void addTags(SpanCustomizer result, MessageChannel channel) {
		if (channel != null) {
			result.tag("channel", messageChannelName(channel));
		}
	}

	private void addTags(Span.Builder result, MessageChannel channel) {
		if (channel != null) {
			result.tag("channel", messageChannelName(channel));
		}
	}

}
