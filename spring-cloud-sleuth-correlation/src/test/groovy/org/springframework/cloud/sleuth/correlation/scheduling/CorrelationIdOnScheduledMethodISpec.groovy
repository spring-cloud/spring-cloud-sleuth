package org.springframework.cloud.sleuth.correlation.scheduling

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cloud.sleuth.correlation.base.BaseConfiguration
import org.springframework.cloud.sleuth.correlation.CorrelationIdAutoConfiguration
import org.springframework.test.context.ContextConfiguration
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

@ContextConfiguration(classes = [TaskSchedulingConfiguration, ScheduledBeanConfiguration, CorrelationIdAutoConfiguration, BaseConfiguration])
class CorrelationIdOnScheduledMethodISpec extends Specification {

	@Autowired
	TestBeanWithScheduledMethod beanWithScheduledMethod

	def "should have correlationId set after scheduled method has been called"() {
		PollingConditions conditions = new PollingConditions(timeout: 1.5, initialDelay: 0.1, factor: 1.05)
		expect:
			conditions.eventually {
				beanWithScheduledMethod.correlationId != null
			}
	}

}
