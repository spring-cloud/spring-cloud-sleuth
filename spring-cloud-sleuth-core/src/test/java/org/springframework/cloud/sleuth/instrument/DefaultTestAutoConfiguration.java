package org.springframework.cloud.sleuth.instrument;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.cloud.client.loadbalancer.LoadBalancerAutoConfiguration;
import org.springframework.cloud.netflix.archaius.ArchaiusAutoConfiguration;
import org.springframework.cloud.sleuth.instrument.integration.TraceSpringIntegrationAutoConfiguration;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@EnableAutoConfiguration(exclude = { TraceSpringIntegrationAutoConfiguration.class,
		ArchaiusAutoConfiguration.class, LoadBalancerAutoConfiguration.class })
public @interface DefaultTestAutoConfiguration {
}
