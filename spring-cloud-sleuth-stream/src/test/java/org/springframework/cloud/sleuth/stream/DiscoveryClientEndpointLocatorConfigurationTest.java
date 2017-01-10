package org.springframework.cloud.sleuth.stream;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Matcin Wielgus
 */
public class DiscoveryClientEndpointLocatorConfigurationTest {
    @Test
    public void endpointLocatorShouldDefaultToServerPropertiesEndpointLocator() {
        try (ConfigurableApplicationContext ctxt = new SpringApplication(
                EmptyConfiguration.class).run("--spring.main.web_environment=false")) {
            assertThat(ctxt.getBean(HostLocator.class))
                    .isInstanceOf(ServerPropertiesHostLocator.class);
        }
    }

    @Test
    public void endpointLocatorShouldDefaultToServerPropertiesEndpointLocatorEvenWhenDiscoveryClientPresent() {
        try (ConfigurableApplicationContext ctxt = new SpringApplication(
                ConfigurationWithDiscoveryClient.class)
                .run("--spring.main.web_environment=false")) {
            assertThat(ctxt.getBean(HostLocator.class))
                    .isInstanceOf(ServerPropertiesHostLocator.class);
        }
    }

    @Test
    public void endpointLocatorShouldRespectExistingEndpointLocator() {
        try (ConfigurableApplicationContext ctxt = new SpringApplication(
                ConfigurationWithCustomLocator.class)
                .run("--spring.main.web_environment=false")) {
            assertThat(ctxt.getBean(HostLocator.class))
                    .isSameAs(ConfigurationWithCustomLocator.locator);
        }
    }

    @Test
    public void endpointLocatorShouldBeFallbackHavingEndpointLocatorWhenAskedTo() {
        try (ConfigurableApplicationContext ctxt = new SpringApplication(
                ConfigurationWithDiscoveryClient.class).run(
                "--spring.zipkin.discoveryLocalEndpointLocator=true",
                "--spring.main.web_environment=false")) {
            assertThat(ctxt.getBean(HostLocator.class))
                    .isInstanceOf(DiscoveryClientHostLocator.class);
        }
    }

    @Test
    public void endpointLocatorShouldRespectExistingEndpointLocatorEvenWhenAskedToBeDiscovery() {
        try (ConfigurableApplicationContext ctxt = new SpringApplication(
                ConfigurationWithDiscoveryClient.class,
                ConfigurationWithCustomLocator.class).run(
                "--spring.zipkin.discoveryLocalEndpointLocator=true",
                "--spring.main.web_environment=false")) {
            assertThat(ctxt.getBean(HostLocator.class))
                    .isSameAs(ConfigurationWithCustomLocator.locator);
        }
    }

    @Configuration
    @EnableAutoConfiguration
    public static class EmptyConfiguration {
    }

    @Configuration
    @EnableAutoConfiguration
    public static class ConfigurationWithDiscoveryClient {
        @Bean
        public DiscoveryClient getDiscoveryClient() {
            return Mockito.mock(DiscoveryClient.class);
        }
    }

    @Configuration
    @EnableAutoConfiguration
    public static class ConfigurationWithCustomLocator {
        static HostLocator locator = Mockito.mock(HostLocator.class);

        @Bean
        public HostLocator getEndpointLocator() {
            return locator;
        }
    }
}