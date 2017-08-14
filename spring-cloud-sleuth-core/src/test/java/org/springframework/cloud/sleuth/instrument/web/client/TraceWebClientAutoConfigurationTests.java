package org.springframework.cloud.sleuth.instrument.web.client;

import org.assertj.core.api.BDDAssertions;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.resource.UserInfoRestTemplateCustomizer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.sleuth.autoconfig.TraceAutoConfiguration;
import org.springframework.cloud.sleuth.instrument.web.TraceHttpAutoConfiguration;
import org.springframework.cloud.sleuth.instrument.web.TraceWebAutoConfiguration;
import org.springframework.cloud.sleuth.log.SleuthLogAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.OAuth2RestTemplate;
import org.springframework.security.oauth2.client.resource.OAuth2ProtectedResourceDetails;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author Marcin Grzejszczak
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = TraceWebClientAutoConfigurationTests.Config.class)
public class TraceWebClientAutoConfigurationTests {

	@Autowired Config config;
	@Autowired UserInfoRestTemplateCustomizer customizer;
	@Autowired TraceRestTemplateInterceptor interceptor;

	@Test
	public void should_wrap_UserInfoRestTemplateCustomizer_in_a_trace_representation() {
		OAuth2ProtectedResourceDetails details = Mockito.mock(OAuth2ProtectedResourceDetails.class);
		OAuth2RestTemplate template = new OAuth2RestTemplate(details);

		this.customizer.customize(template);

		then(this.config.executed).isTrue();
		then(template.getInterceptors()).contains(this.interceptor);
	}


	@Configuration
	@ImportAutoConfiguration(classes = {
			TraceWebClientAutoConfiguration.class, SleuthLogAutoConfiguration.class,
			TraceHttpAutoConfiguration.class, TraceWebAutoConfiguration.class, TraceAutoConfiguration.class })
	static class Config {

		boolean executed = false;

		@Bean UserInfoRestTemplateCustomizer customizer() {
			return new UserInfoRestTemplateCustomizer() {
				@Override public void customize(OAuth2RestTemplate template) {
					Config.this.executed = true;
				}
			};
		}
	}
}