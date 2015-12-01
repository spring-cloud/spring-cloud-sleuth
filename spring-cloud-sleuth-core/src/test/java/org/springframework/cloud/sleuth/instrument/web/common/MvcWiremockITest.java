package org.springframework.cloud.sleuth.instrument.web.common;

import org.junit.Before;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.sleuth.IdGenerator;
import org.springframework.cloud.sleuth.TraceManager;
import org.springframework.cloud.sleuth.instrument.web.TraceFilter;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder;

import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;

/**
 * Base specification for tests that use Wiremock as HTTP server stub.
 * By extending this specification you gain a bean with {@link HttpMockServer} and a {@link WireMock}
 *
 * @author 4financeIT
 * @see MockServerConfiguration
 * @see WireMock
 * @see HttpMockServer
 *
 * @author 4financeIT
 */
@ContextConfiguration(classes = {MockServerConfiguration.class})
public abstract class MvcWiremockITest extends MvcITest {

	protected WireMock wireMock;
	@Autowired protected HttpMockServer httpMockServer;
	@Autowired protected TraceManager trace;
	@Autowired protected IdGenerator idGenerator;

	@Override
	@Before
	public void setup() {
		super.setup();
		this.wireMock = new WireMock("localhost", this.httpMockServer.port());
		this.wireMock.resetToDefaultMappings();
	}

	protected void stubInteraction(MappingBuilder mapping, ResponseDefinitionBuilder response) {
		this.wireMock.register(mapping.willReturn(response));
	}

	@Override
	protected void configureMockMvcBuilder(DefaultMockMvcBuilder mockMvcBuilder) {
		mockMvcBuilder.addFilters(new TraceFilter(this.trace, idGenerator));
	}
}
