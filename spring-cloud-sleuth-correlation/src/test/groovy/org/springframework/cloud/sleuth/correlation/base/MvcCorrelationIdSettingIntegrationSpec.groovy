package org.springframework.cloud.sleuth.correlation.base

import org.springframework.cloud.sleuth.correlation.CorrelationIdFilter
import org.springframework.test.web.servlet.setup.ConfigurableMockMvcBuilder

class MvcCorrelationIdSettingIntegrationSpec extends org.springframework.cloud.sleuth.correlation.base.MvcWiremockIntegrationSpec {

	@Override
	protected void configureMockMvcBuilder(ConfigurableMockMvcBuilder mockMvcBuilder) {
		super.configureMockMvcBuilder(mockMvcBuilder)
		mockMvcBuilder.addFilter(new CorrelationIdFilter())
	}
}
