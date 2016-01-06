package org.springframework.cloud.sleuth.sampler;

import org.springframework.core.convert.converter.Converter;

import java.util.UUID;

/**
 * Interface showing how to convert String into UUID
 *
 * @author Marcin Grzejszczak
 * @author Adrian Cole
 */
public interface StringToUuidConverter extends Converter<String, UUID> {

	@Override
	UUID convert(String source);
}
