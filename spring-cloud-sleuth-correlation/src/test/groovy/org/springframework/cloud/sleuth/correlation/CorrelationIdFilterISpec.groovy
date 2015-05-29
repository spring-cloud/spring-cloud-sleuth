package org.springframework.cloud.sleuth.correlation

import org.slf4j.MDC
import org.springframework.boot.test.SpringApplicationContextLoader
import org.springframework.cloud.sleuth.correlation.base.BaseConfiguration
import org.springframework.cloud.sleuth.correlation.base.MvcCorrelationIdSettingIntegrationSpec
import org.springframework.http.MediaType
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders

@ContextConfiguration(classes = [BaseConfiguration, CorrelationIdAutoConfiguration], loader = SpringApplicationContextLoader)
class CorrelationIdFilterISpec extends MvcCorrelationIdSettingIntegrationSpec {

	def "should create and return correlationId in HTTP header"() {
		when:
			MvcResult mvcResult = sendPingWithoutCorrelationId()

		then:
			getCorrelationIdFromResponseHeader(mvcResult) != null
	}

	def "when correlationId is sent, should not create a new one, but return the existing one instead"() {
		given:
			String passedCorrelationId = "passedCorId"

		when:
			MvcResult mvcResult = sendPingWithCorrelationId(passedCorrelationId)

		then:
			getCorrelationIdFromResponseHeader(mvcResult) == passedCorrelationId
	}

	def "should clean up MDC after the call"() {
		given:
			String passedCorrelationId = "passedCorId"

		when:
			sendPingWithCorrelationId(passedCorrelationId)

		then:
			MDC.get(CorrelationIdHolder.CORRELATION_ID_HEADER) == null
	}

	private MvcResult sendPingWithCorrelationId(String passedCorrelationId) {
		mockMvc.perform(MockMvcRequestBuilders.get('/ping').accept(MediaType.TEXT_PLAIN)
				.header(CorrelationIdHolder.CORRELATION_ID_HEADER, passedCorrelationId)).andReturn()
	}

	private MvcResult sendPingWithoutCorrelationId() {
		mockMvc.perform(MockMvcRequestBuilders.get('/ping').accept(MediaType.TEXT_PLAIN)).andReturn()
	}

	private String getCorrelationIdFromResponseHeader(MvcResult mvcResult) {
		mvcResult.response.getHeader(CorrelationIdHolder.CORRELATION_ID_HEADER)
	}
}
