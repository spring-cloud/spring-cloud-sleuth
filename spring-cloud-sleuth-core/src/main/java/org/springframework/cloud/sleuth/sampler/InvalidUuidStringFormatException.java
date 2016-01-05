package org.springframework.cloud.sleuth.sampler;

/**
 * Runtime exception thrown when the String can't be parsed to UUID
 */
public class InvalidUuidStringFormatException extends RuntimeException {
	public InvalidUuidStringFormatException(String sourceString) {
		super("String [" + sourceString + "] can't be parsed as UUID");
	}
}
