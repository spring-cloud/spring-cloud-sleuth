package org.springframework.cloud.sleuth.correlation

import groovyx.gpars.GParsPool
import spock.lang.Specification

import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class CorrelationIdUpdaterSpec extends Specification {

	def cleanup() {
		CorrelationIdHolder.remove()
	}

	def "correlation ID should not be propagated to other thread by default"() {
		given:
			CorrelationIdUpdater.updateCorrelationId('A')
		expect:
			GParsPool.withPool(1) {
				["1"].eachParallel {
					assert CorrelationIdHolder.get() == null
				}
			}
	}

	def "should propagate correlation ID into nested Callable"() {
		given:
			ExecutorService threadPool = Executors.newFixedThreadPool(1)
			CorrelationIdUpdater.updateCorrelationId('A')
			Callable<String> callable = new CorrelationIdTestCallable()
		when:
			Callable<String> wrappedCallable = CorrelationIdUpdater.wrapCallableWithId(callable)
			String nestedCorrelationId = threadPool.submit(wrappedCallable).get(1, TimeUnit.SECONDS)
		then:
			nestedCorrelationId == 'A'
		cleanup:
			threadPool.shutdown()
	}

	def "should restore previous correlation ID after Callable execution in other thread"() {
		given:
			ExecutorService threadPool = Executors.newFixedThreadPool(1)
			CorrelationIdUpdater.updateCorrelationId('A')
			Callable<String> callable = new CorrelationIdTestCallable()
		and:
			threadPool.submit({ CorrelationIdHolder.set('B') }).get(1, TimeUnit.SECONDS)
		when:
			threadPool.submit(CorrelationIdUpdater.wrapCallableWithId(callable)).get(1, TimeUnit.SECONDS)
		then:
			def restoredCorrelationId = threadPool.submit({
				CorrelationIdHolder.get()
			} as Callable).get(1, TimeUnit.SECONDS)
			restoredCorrelationId == 'B'
		cleanup:
			threadPool.shutdown()
	}

	private static class CorrelationIdTestCallable implements Callable<String> {
		@Override
		String call() throws Exception {
			CorrelationIdHolder.get()
		}
	}
}
