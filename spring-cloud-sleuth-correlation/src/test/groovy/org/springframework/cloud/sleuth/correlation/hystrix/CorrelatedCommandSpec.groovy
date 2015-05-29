package org.springframework.cloud.sleuth.correlation.hystrix

import com.netflix.hystrix.HystrixCommand
import com.netflix.hystrix.HystrixCommandGroupKey
import org.springframework.cloud.sleuth.correlation.CorrelationIdHolder
import org.springframework.cloud.sleuth.correlation.CorrelationIdUpdater
import spock.lang.Specification

class CorrelatedCommandSpec extends Specification {

	public static final String CORRELATION_ID = 'A'

	def 'should run Hystrix command with client correlation ID'() {
		given:
			CorrelationIdUpdater.updateCorrelationId(CORRELATION_ID)
			def command = new CorrelatedCommand<String>(HystrixCommand.Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey(""))) {
				String doRun() {
					return CorrelationIdHolder.get()
				}
			}
		when:
			def result = command.execute()
		then:
			result == CORRELATION_ID
	}

	def 'should run Hystrix command in different thread'() {
		given:
			def command = new CorrelatedCommand<String>(HystrixCommand.Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey(""))) {
				String doRun() {
					return Thread.currentThread().name
				}
			}
		when:
			def threadName = command.execute()
		then:
			Thread.currentThread().name != threadName
	}

}
