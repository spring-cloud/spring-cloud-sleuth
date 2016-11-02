package org.springframework.cloud.sleuth.instrument.web.client;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.lang.annotation.*;

/**
 * Helper annotation to enable Sleuth web client
 *
 * @author Marcin Grzejszczak
 * @since 1.0.11
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE, ElementType.METHOD})
@Documented
@ConditionalOnProperty(value = "spring.sleuth.web.client.enabled", matchIfMissing = true)
@interface SleuthWebClientEnabled {
}
