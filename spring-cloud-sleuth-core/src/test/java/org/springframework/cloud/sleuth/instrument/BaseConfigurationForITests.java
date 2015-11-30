package org.springframework.cloud.sleuth.instrument;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jmx.JmxAutoConfiguration;
import org.springframework.cloud.client.loadbalancer.LoadBalancerAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;

@EnableAspectJAutoProxy(proxyTargetClass = true)
@Configuration
@EnableAutoConfiguration(exclude = {LoadBalancerAutoConfiguration.class, JmxAutoConfiguration.class})
public class BaseConfigurationForITests {

	@Bean static PropertySourcesPlaceholderConfigurer placeholderConfigurer() {
		return new PropertySourcesPlaceholderConfigurer();
	}

}
