package org.springframework.cloud.sleuth.instrument.web;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.instrument.DefaultTestAutoConfiguration;
import org.springframework.cloud.sleuth.instrument.web.common.HttpMockServer;
import org.springframework.cloud.sleuth.instrument.web.common.AbstractMvcWiremockIntegrationTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.test.context.junit4.rules.SpringClassRule;
import org.springframework.test.context.junit4.rules.SpringMethodRule;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.async.WebAsyncTask;

import java.util.concurrent.Callable;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static java.util.concurrent.TimeUnit.SECONDS;
import static junitparams.JUnitParamsRunner.$;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringApplicationConfiguration(classes = {RestTemplateTraceAspectIntegrationTests.CorrelationIdAspectTestConfiguration.class})
@RunWith(JUnitParamsRunner.class)
public class RestTemplateTraceAspectIntegrationTests extends AbstractMvcWiremockIntegrationTest {

	@ClassRule public static final SpringClassRule SCR = new SpringClassRule();
	@Rule public final SpringMethodRule springMethodRule = new SpringMethodRule();

	@Before public void setupDefaultWireMockStubbing() {
		stubInteraction(get(urlMatching(".*")), aResponse().withStatus(200));
	}

	@Test
	public void should_set_span_data_on_headers_via_aspect_in_synchronous_call() throws Exception {
		whenARequestIsSentToASyncEndpoint();

		thenTraceIdHasBeenSetOnARequestHeader();
	}

	@Test
	@Parameters
	public void should_set_span_data_on_headers_via_aspect_in_asynchronous_call(String url) throws Exception {
		whenARequestIsSentToAnAsyncEndpoint(url);

		thenTraceIdHasBeenSetOnARequestHeader();
	}

	public Object[] parametersForShould_set_span_data_on_headers_via_aspect_in_asynchronous_call() {
		return $("/callablePing", "/webAsyncTaskPing");
	}

	private void whenARequestIsSentToASyncEndpoint() throws Exception {
		this.mockMvc.perform(MockMvcRequestBuilders.get("/syncPing").accept(MediaType.TEXT_PLAIN)).andReturn();
	}

	private void thenTraceIdHasBeenSetOnARequestHeader() {
		this.wireMock.verifyThat(getRequestedFor(urlMatching(".*")).withHeader(Span.TRACE_ID_NAME, matching("^(?!\\s*$).+")));
	}

	private void whenARequestIsSentToAnAsyncEndpoint(String url) throws Exception {
		MvcResult mvcResult = this.mockMvc.perform(MockMvcRequestBuilders.get(url).accept(MediaType.TEXT_PLAIN))
				.andExpect(request().asyncStarted())
				.andReturn();
		mvcResult.getAsyncResult(SECONDS.toMillis(2));
		this.mockMvc.perform(asyncDispatch(mvcResult)).
				andDo(print()).
				andExpect(status().isOk());
	}

	@EnableAsync
	@DefaultTestAutoConfiguration
	@Import(AspectTestingController.class)
	public static class CorrelationIdAspectTestConfiguration {
	}

	@RestController
	public static class AspectTestingController {

		@Autowired HttpMockServer httpMockServer;
		@Autowired RestTemplate restTemplate;

		@RequestMapping(value = "/syncPing", method = RequestMethod.GET, produces = MediaType.TEXT_PLAIN_VALUE)
		public String syncPing() {
			return callWiremockAndReturnOk();
		}

		@RequestMapping(value = "/callablePing", method = RequestMethod.GET, produces = MediaType.TEXT_PLAIN_VALUE)
		public Callable<String> asyncPing() {
			return new Callable<String>() {
				@Override
				public String call() throws Exception {
					return callWiremockAndReturnOk();
				}
			};
		}

		@RequestMapping(value = "/webAsyncTaskPing", method = RequestMethod.GET, produces = MediaType.TEXT_PLAIN_VALUE)
		public WebAsyncTask<String> webAsyncTaskPing() {
			return new WebAsyncTask<>(new Callable<String>() {
				@Override
				public String call() throws Exception {
					return callWiremockAndReturnOk();
				}
			});
		};

		private String callWiremockAndReturnOk() {
			this.restTemplate.getForObject("http://localhost:" + this.httpMockServer.port(), String.class);
			return "OK";
		}
	}
}
