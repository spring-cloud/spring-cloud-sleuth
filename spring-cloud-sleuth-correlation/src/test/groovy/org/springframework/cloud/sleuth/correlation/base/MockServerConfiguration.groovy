package org.springframework.cloud.sleuth.correlation.base

import groovy.transform.CompileStatic
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.util.SocketUtils

/**
 * Configuration that registers {@link HttpMockServer} as a Spring bean. Takes care
 * of graceful shutdown process.
 *
 * @see HttpMockServer
 */
@CompileStatic
@Configuration
class MockServerConfiguration {

	@Bean(destroyMethod = 'shutdownServer')
	HttpMockServer httpMockServer() {
		HttpMockServer httpMockServer = new HttpMockServer(SocketUtils.findAvailableTcpPort())
		httpMockServer.start()
		return httpMockServer
	}

}
