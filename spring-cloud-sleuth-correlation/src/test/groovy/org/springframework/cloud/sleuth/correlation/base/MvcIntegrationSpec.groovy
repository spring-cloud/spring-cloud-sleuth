package org.springframework.cloud.sleuth.correlation.base

import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.test.context.web.WebAppConfiguration
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.ConfigurableMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import spock.lang.Specification

/**
 * Base for specifications that use Spring's {@link MockMvc}. Provides also {@link WebApplicationContext}, 
 * {@link ApplicationContext}. The latter you can use to specify what
 * kind of address should be returned for a given dependency name. 
 *
 * @see WebApplicationContext
 * @see ApplicationContext
 */
@CompileStatic
@WebAppConfiguration
abstract class MvcIntegrationSpec extends Specification {

	@Autowired
	protected WebApplicationContext webApplicationContext
	@Autowired
	protected ApplicationContext applicationContext

	protected MockMvc mockMvc

	void setup() {
		ConfigurableMockMvcBuilder mockMvcBuilder = MockMvcBuilders.webAppContextSetup(webApplicationContext)
		configureMockMvcBuilder(mockMvcBuilder)
		mockMvc = mockMvcBuilder.build()
	}

	/**
	 * Override in a subclass to modify mockMvcBuilder configuration (e.g. add filter).
	 *
	 * The method from super class should be called.
	 */
	protected void configureMockMvcBuilder(ConfigurableMockMvcBuilder mockMvcBuilder) {
	}
}
