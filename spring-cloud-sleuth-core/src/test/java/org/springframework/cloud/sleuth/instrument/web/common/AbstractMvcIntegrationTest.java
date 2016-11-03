package org.springframework.cloud.sleuth.instrument.web.common;

import org.junit.Before;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.sleuth.HttpSpanExtractor;
import org.springframework.cloud.sleuth.TraceKeys;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.instrument.web.HttpTraceKeysInjector;
import org.springframework.cloud.sleuth.trace.TestSpanContextHolder;
import org.springframework.cloud.sleuth.util.ExceptionUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Base for specifications that use Spring's {@link MockMvc}. Provides also {@link WebApplicationContext},
 * {@link ApplicationContext}. The latter you can use to specify what
 * kind of address should be returned for a given dependency name.
 *
 * @see WebApplicationContext
 * @see ApplicationContext
 *
 * @author 4financeIT
 */
@WebAppConfiguration
public abstract class AbstractMvcIntegrationTest {

	@Autowired protected WebApplicationContext webApplicationContext;
	protected MockMvc mockMvc;
	@Autowired protected Tracer tracer;
	@Autowired protected TraceKeys traceKeys;
	@Autowired protected HttpSpanExtractor spanExtractor;
	@Autowired protected HttpTraceKeysInjector httpTraceKeysInjector;

	@Before
	public void setup() {
		ExceptionUtils.setFail(true);
		TestSpanContextHolder.removeCurrentSpan();
		DefaultMockMvcBuilder mockMvcBuilder = MockMvcBuilders.webAppContextSetup(this.webApplicationContext);
		configureMockMvcBuilder(mockMvcBuilder);
		this.mockMvc = mockMvcBuilder.build();
	}

	/**
	 * Override in a subclass to modify mockMvcBuilder configuration (e.g. add filter).
	 * <p>
	 * The method from super class should be called.
	 */
	protected void configureMockMvcBuilder(DefaultMockMvcBuilder mockMvcBuilder) {
	}
}
