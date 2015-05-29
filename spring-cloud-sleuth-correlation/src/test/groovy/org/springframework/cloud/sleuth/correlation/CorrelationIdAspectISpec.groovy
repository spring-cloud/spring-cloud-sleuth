package org.springframework.cloud.sleuth.correlation
import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import groovy.transform.TypeChecked
import org.hamcrest.Description
import org.hamcrest.TypeSafeMatcher
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.SpringApplicationContextLoader
import org.springframework.cloud.sleuth.correlation.base.HttpMockServer
import org.springframework.cloud.sleuth.correlation.base.MvcCorrelationIdSettingIntegrationSpec
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.EnableAspectJAutoProxy
import org.springframework.http.MediaType
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultHandlers
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.client.RestTemplate

import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit

import static com.github.tomakehurst.wiremock.client.WireMock.*
import static org.springframework.cloud.sleuth.correlation.CorrelationIdHolder.CORRELATION_ID_HEADER

@ContextConfiguration(classes = [CorrelationIdAspectSpecConfiguration], loader = SpringApplicationContextLoader)
class CorrelationIdAspectISpec extends MvcCorrelationIdSettingIntegrationSpec {

	public static final String CORRELATION_ID_PATTERN = /^(?!\s*$).+/
	public static final TypeSafeMatcher<String> hasCorrelationIdSet = new TypeSafeMatcher<String>() {
		@Override
		protected boolean matchesSafely(String item) {
			return item.matches(CORRELATION_ID_PATTERN)
		}

		@Override
		void describeTo(Description description) {

		}
	}

	def "should set correlationId on header via aspect in synchronous call"() {
		given:
			stubInteraction(get(urlMatching('.*')), aResponse().withStatus(200))
		when:
			mockMvc.perform(MockMvcRequestBuilders.get('/syncPing').accept(MediaType.TEXT_PLAIN))
					.andExpect(MockMvcResultMatchers.header().string(CORRELATION_ID_HEADER, hasCorrelationIdSet))
		then:
			wireMock.verifyThat(getRequestedFor(urlMatching('.*')).withHeader(CORRELATION_ID_HEADER, matching(CORRELATION_ID_PATTERN)))

	}

	def "should set correlationId on header via aspect in asynchronous call"() {
		given:
			stubInteraction(get(urlMatching('.*')), aResponse().withStatus(200))
		when:
			MvcResult mvcResult = mockMvc.perform(MockMvcRequestBuilders.get('/asyncPing').accept(MediaType.TEXT_PLAIN))
					.andExpect(MockMvcResultMatchers.request().asyncStarted())
					.andReturn()
		and:
			mvcResult.getAsyncResult(TimeUnit.SECONDS.toMillis(2))
		then:
			mockMvc.perform(MockMvcRequestBuilders.asyncDispatch(mvcResult)).
					andDo(MockMvcResultHandlers.print()).
					andExpect(MockMvcResultMatchers.status().isOk()).
					andExpect(MockMvcResultMatchers.header().string(CORRELATION_ID_HEADER, hasCorrelationIdSet))
		and:
			wireMock.verifyThat(getRequestedFor(urlMatching('.*')).withHeader(CORRELATION_ID_HEADER, matching(CORRELATION_ID_PATTERN)))
	}

	@CompileStatic
	@Configuration
	@EnableAsync
	@EnableAutoConfiguration
	@EnableAspectJAutoProxy(proxyTargetClass = true)
	static class CorrelationIdAspectSpecConfiguration {
		@Bean
		AspectTestingController aspectTestingController() {
			return new AspectTestingController()
		}
	}

	@RestController
	@TypeChecked
	@PackageScope
	static class AspectTestingController {

		@Autowired
		private HttpMockServer httpMockServer
		@Autowired
		private RestTemplate restTemplate

		@RequestMapping(value = "/syncPing", method = RequestMethod.GET, produces = MediaType.TEXT_PLAIN_VALUE)
		String syncPing() {
			callWiremockAndReturnOk()
		}

		@RequestMapping(value = "/asyncPing", method = RequestMethod.GET, produces = MediaType.TEXT_PLAIN_VALUE)
		Callable<String> asyncPing() {
			return {
				callWiremockAndReturnOk()
			}
		}

		private String callWiremockAndReturnOk() {
			restTemplate.getForObject("http://localhost:${httpMockServer.port()}", String)
			return "OK"
		}
	}

}
