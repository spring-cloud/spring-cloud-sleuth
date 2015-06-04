package org.springframework.cloud.sleuth.correlation.base

import com.github.tomakehurst.wiremock.client.MappingBuilder
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ContextConfiguration

/**
 * Base specification for tests that use Wiremock as HTTP server stub.
 * By extending this specification you gain a bean with {@link HttpMockServer} and a {@link WireMock}
 * instance that you can stub by using {@link MvcWiremockIntegrationSpec#stubInteraction(com.github.tomakehurst.wiremock.client.MappingBuilder, com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder)}
 *
 * @see MockServerConfiguration
 * @see WireMock
 * @see HttpMockServer
 * @see MvcIntegrationSpec
 */
@CompileStatic
@ContextConfiguration(classes = [MockServerConfiguration])
abstract class MvcWiremockIntegrationSpec extends MvcIntegrationSpec {

	@Autowired
	protected HttpMockServer httpMockServer
	protected WireMock wireMock

	void setup() {
		wireMock = new WireMock('localhost', httpMockServer.port())
		wireMock.resetToDefaultMappings()
	}

	protected void stubInteraction(MappingBuilder mapping, ResponseDefinitionBuilder response) {
		wireMock.register(mapping.willReturn(response))
	}

}
