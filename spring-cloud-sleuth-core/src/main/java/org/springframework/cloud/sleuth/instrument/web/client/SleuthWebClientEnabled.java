package org.springframework.cloud.sleuth.instrument.web.client;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

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
