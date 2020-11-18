/*
 * Copyright 2013-2020 the original author or authors.
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

package org.springframework.cloud.sleuth.zipkin2;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.autoconfigure.quartz.QuartzAutoConfiguration;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.client.serviceregistry.Registration;
import org.springframework.cloud.commons.util.InetUtils;
import org.springframework.cloud.commons.util.InetUtilsProperties;
import org.springframework.cloud.gateway.config.GatewayAutoConfiguration;
import org.springframework.cloud.gateway.config.GatewayClassPathWarningAutoConfiguration;
import org.springframework.cloud.gateway.config.GatewayMetricsAutoConfiguration;
import org.springframework.cloud.sleuth.brave.autoconfig.BraveAutoConfiguration;
import org.springframework.cloud.sleuth.otel.autoconfig.OtelAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Matcin Wielgus
 */
public abstract class DefaultEndpointLocatorConfigurationTest {

	public static final byte[] ADDRESS1234 = { 1, 2, 3, 4 };

	Environment environment = new MockEnvironment();

	@Test
	public void endpointLocatorShouldDefaultToServerPropertiesEndpointLocator() {
		new ApplicationContextRunner().withUserConfiguration(emptyConfiguration())
				.withPropertyValues("spring.jmx.enabled=false")
				.run(ctxt -> assertThat(ctxt).hasSingleBean(DefaultEndpointLocator.class));
	}

	protected Class emptyConfiguration() {
		throw new UnsupportedOperationException("Provide configuration");
	}

	@Test
	public void endpointLocatorShouldDefaultToServerPropertiesEndpointLocatorEvenWhenDiscoveryClientPresent() {
		new ApplicationContextRunner().withUserConfiguration(configurationWithRegistrationClass())
				.withPropertyValues("spring.jmx.enabled=false")
				.run(ctxt -> assertThat(ctxt).hasSingleBean(DefaultEndpointLocator.class));
	}

	protected Class configurationWithRegistrationClass() {
		throw new UnsupportedOperationException("Provide configuration");
	}

	@Test
	public void endpointLocatorShouldRespectExistingEndpointLocator() {
		new ApplicationContextRunner().withUserConfiguration(configurationWithCustomLocatorClass())
				.withPropertyValues("spring.jmx.enabled=false")
				.run(ctxt -> assertThat(ctxt.getBean(EndpointLocator.class)).isSameAs(locatorFromConfiguration()));
	}

	protected Class configurationWithCustomLocatorClass() {
		throw new UnsupportedOperationException("Provide configuration");
	}

	@Test
	public void endpointLocatorShouldSetServiceNameToServiceId() {
		new ApplicationContextRunner().withUserConfiguration(configurationWithRegistrationClass())
				.withPropertyValues("spring.jmx.enabled=false", "spring.zipkin.locator.discovery.enabled=true")
				.run(ctxt -> assertThat(ctxt.getBean(EndpointLocator.class).local().serviceName())
						.isEqualTo("from-registration"));
	}

	@Test
	public void endpointLocatorShouldAcceptServiceNameOverride() {
		new ApplicationContextRunner().withUserConfiguration(configurationWithRegistrationClass())
				.withPropertyValues("spring.jmx.enabled=false", "spring.zipkin.locator.discovery.enabled=true",
						"spring.zipkin.service.name=foo")
				.run(ctxt -> assertThat(ctxt.getBean(EndpointLocator.class).local().serviceName()).isEqualTo("foo"));
	}

	@Test
	public void endpointLocatorShouldRespectExistingEndpointLocatorEvenWhenAskedToBeDiscovery() {
		new ApplicationContextRunner()
				.withUserConfiguration(configurationWithRegistrationClass(), configurationWithCustomLocatorClass())
				.withPropertyValues("spring.jmx.enabled=false", "spring.zipkin.locator.discovery.enabled=true")
				.run(ctxt -> assertThat(ctxt.getBean(EndpointLocator.class)).isSameAs(locatorFromConfiguration()));
	}

	protected EndpointLocator locatorFromConfiguration() {
		throw new UnsupportedOperationException("Provide the locator");
	}

	@Test
	public void portDefaultsTo8080() throws UnknownHostException {
		DefaultEndpointLocator locator = new DefaultEndpointLocator(null, new ServerProperties(), this.environment,
				new ZipkinProperties(), localAddress(ADDRESS1234));

		assertThat(locator.local().port()).isEqualTo(8080);
	}

	@Test
	public void portFromServerProperties() throws UnknownHostException {
		ServerProperties properties = new ServerProperties();
		properties.setPort(1234);

		DefaultEndpointLocator locator = new DefaultEndpointLocator(null, properties, this.environment,
				new ZipkinProperties(), localAddress(ADDRESS1234));

		assertThat(locator.local().port()).isEqualTo(1234);
	}

	@Test
	public void portDefaultsToLocalhost() throws UnknownHostException {
		DefaultEndpointLocator locator = new DefaultEndpointLocator(null, new ServerProperties(), this.environment,
				new ZipkinProperties(), localAddress(ADDRESS1234));

		assertThat(locator.local().ipv4()).isEqualTo("1.2.3.4");
	}

	@Test
	public void hostFromServerPropertiesIp() throws UnknownHostException {
		ServerProperties properties = new ServerProperties();
		properties.setAddress(InetAddress.getByAddress(ADDRESS1234));

		DefaultEndpointLocator locator = new DefaultEndpointLocator(null, properties, this.environment,
				new ZipkinProperties(), localAddress(new byte[] { 4, 4, 4, 4 }));

		assertThat(locator.local().ipv4()).isEqualTo("1.2.3.4");
	}

	@Test
	public void appNameFromProperties() throws UnknownHostException {
		ServerProperties properties = new ServerProperties();
		ZipkinProperties zipkinProperties = new ZipkinProperties();
		zipkinProperties.getService().setName("foo");

		DefaultEndpointLocator locator = new DefaultEndpointLocator(null, properties, this.environment,
				zipkinProperties, localAddress(ADDRESS1234));

		assertThat(locator.local().serviceName()).isEqualTo("foo");
	}

	@Test
	public void negativePortFromServerProperties() throws UnknownHostException {
		ServerProperties properties = new ServerProperties();
		properties.setPort(-1);

		DefaultEndpointLocator locator = new DefaultEndpointLocator(null, properties, this.environment,
				new ZipkinProperties(), localAddress(ADDRESS1234));

		assertThat(locator.local().port()).isEqualTo(8080);
	}

	private InetUtils localAddress(byte[] address) throws UnknownHostException {
		InetUtils mocked = Mockito.spy(new InetUtils(new InetUtilsProperties()));
		Mockito.when(mocked.findFirstNonLoopbackAddress()).thenReturn(InetAddress.getByAddress(address));
		return mocked;
	}

	@Configuration(proxyBeanMethods = false)
	@EnableAutoConfiguration(exclude = { GatewayClassPathWarningAutoConfiguration.class, GatewayAutoConfiguration.class,
			GatewayMetricsAutoConfiguration.class, ManagementWebSecurityAutoConfiguration.class,
			MongoAutoConfiguration.class, QuartzAutoConfiguration.class, OtelAutoConfiguration.class })
	public static class BraveEmptyConfiguration {

	}

	@Configuration(proxyBeanMethods = false)
	@EnableAutoConfiguration(exclude = { GatewayClassPathWarningAutoConfiguration.class, GatewayAutoConfiguration.class,
			GatewayMetricsAutoConfiguration.class, ManagementWebSecurityAutoConfiguration.class,
			MongoAutoConfiguration.class, QuartzAutoConfiguration.class, OtelAutoConfiguration.class })
	public static class BraveConfigurationWithRegistration {

		@Bean
		public Registration getRegistration() {
			return new Registration() {
				@Override
				public String getServiceId() {
					return "from-registration";
				}

				@Override
				public String getHost() {
					return null;
				}

				@Override
				public int getPort() {
					return 0;
				}

				@Override
				public boolean isSecure() {
					return false;
				}

				@Override
				public URI getUri() {
					return null;
				}

				@Override
				public Map<String, String> getMetadata() {
					return null;
				}
			};
		}

	}

	@Configuration(proxyBeanMethods = false)
	@EnableAutoConfiguration(exclude = { GatewayClassPathWarningAutoConfiguration.class, GatewayAutoConfiguration.class,
			GatewayMetricsAutoConfiguration.class, ManagementWebSecurityAutoConfiguration.class,
			MongoAutoConfiguration.class, QuartzAutoConfiguration.class, OtelAutoConfiguration.class })
	public static class BraveConfigurationWithCustomLocator {

		static EndpointLocator locator = Mockito.mock(EndpointLocator.class);

		@Bean
		public EndpointLocator getEndpointLocator() {
			return locator;
		}

	}

	@Configuration(proxyBeanMethods = false)
	@EnableAutoConfiguration(exclude = { GatewayClassPathWarningAutoConfiguration.class, GatewayAutoConfiguration.class,
			GatewayMetricsAutoConfiguration.class, ManagementWebSecurityAutoConfiguration.class,
			MongoAutoConfiguration.class, QuartzAutoConfiguration.class, BraveAutoConfiguration.class })
	public static class OtelEmptyConfiguration {

	}

	@Configuration(proxyBeanMethods = false)
	@EnableAutoConfiguration(exclude = { GatewayClassPathWarningAutoConfiguration.class, GatewayAutoConfiguration.class,
			GatewayMetricsAutoConfiguration.class, ManagementWebSecurityAutoConfiguration.class,
			MongoAutoConfiguration.class, QuartzAutoConfiguration.class, BraveAutoConfiguration.class })
	public static class OtelConfigurationWithRegistration {

		@Bean
		public Registration getRegistration() {
			return new Registration() {
				@Override
				public String getServiceId() {
					return "from-registration";
				}

				@Override
				public String getHost() {
					return null;
				}

				@Override
				public int getPort() {
					return 0;
				}

				@Override
				public boolean isSecure() {
					return false;
				}

				@Override
				public URI getUri() {
					return null;
				}

				@Override
				public Map<String, String> getMetadata() {
					return null;
				}
			};
		}

	}

	@Configuration(proxyBeanMethods = false)
	@EnableAutoConfiguration(exclude = { GatewayClassPathWarningAutoConfiguration.class, GatewayAutoConfiguration.class,
			GatewayMetricsAutoConfiguration.class, ManagementWebSecurityAutoConfiguration.class,
			MongoAutoConfiguration.class, QuartzAutoConfiguration.class, BraveAutoConfiguration.class })
	public static class OtelConfigurationWithCustomLocator {

		static EndpointLocator locator = Mockito.mock(EndpointLocator.class);

		@Bean
		public EndpointLocator getEndpointLocator() {
			return locator;
		}

	}

}
