package org.springframework.cloud.sleuth.instrument.integration;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.MessagingException;

class StompMessageHandler implements MessageHandler {

	Message<?> message;

	@Override
	public void handleMessage(Message<?> message) throws MessagingException {
		this.message = message;
	}
}