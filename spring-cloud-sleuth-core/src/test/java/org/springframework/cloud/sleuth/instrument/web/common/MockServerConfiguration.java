package org.springframework.cloud.sleuth.instrument.web.common;

import java.lang.invoke.MethodHandles;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.SocketUtils;

@Configuration
public class MockServerConfiguration {

	private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	@Bean(destroyMethod = "shutdownServer")
	HttpMockServer httpMockServer() {
		return tryToStartMockServer();
	}

	private HttpMockServer tryToStartMockServer() {
		HttpMockServer httpMockServer = null;
		while(httpMockServer == null) {
			try {
				httpMockServer = new HttpMockServer(SocketUtils.findAvailableTcpPort());
				httpMockServer.start();
			} catch (Exception exception) {
				log.warn("Exception occurred while trying to set the port for the Wiremock server", exception);
				httpMockServer = null;
			}
		}
		return httpMockServer;
	}
}
