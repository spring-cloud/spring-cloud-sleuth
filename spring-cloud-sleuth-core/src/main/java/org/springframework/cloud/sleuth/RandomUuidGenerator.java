package org.springframework.cloud.sleuth;

import java.util.UUID;

/**
 * @author Spencer Gibb
 */
public class RandomUuidGenerator implements IdGenerator {

	@Override
	public String create() {
		return UUID.randomUUID().toString();
	}
}
