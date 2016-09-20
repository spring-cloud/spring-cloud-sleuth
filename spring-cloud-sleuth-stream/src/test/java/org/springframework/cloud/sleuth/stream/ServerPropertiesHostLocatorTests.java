/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.sleuth.stream;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;

import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.util.LocalAdressResolver;

import static org.assertj.core.api.Assertions.assertThat;

public class ServerPropertiesHostLocatorTests {
	Span span = new Span(1, 3, "http:name", 1L, Collections.<Long>emptyList(), 2L, true, true,
			"process");

	@Test
	public void portDefaultsTo8080() {
		ServerPropertiesHostLocator locator = new ServerPropertiesHostLocator(
				new ServerProperties(), "unknown",mockResolver("127.0.0.1"));

		assertThat(locator.locate(this.span).getPort()).isEqualTo((short) 8080);
	}

	@Test
	public void portFromServerProperties() {
		ServerProperties properties = new ServerProperties();
		properties.setPort(1234);

		ServerPropertiesHostLocator locator = new ServerPropertiesHostLocator(properties,
				"unknown",mockResolver("127.0.0.1"));

		assertThat(locator.locate(this.span).getPort()).isEqualTo((short) 1234);
	}

	@Test
	public void portDefaultsToLocalhost() {
		ServerPropertiesHostLocator locator = new ServerPropertiesHostLocator(
				new ServerProperties(), "unknown",mockResolver("129.0.0.1"));

		assertThat(locator.locate(this.span).getAddress()).isEqualTo("129.0.0.1");
	}

	@Test
	public void hostFromServerPropertiesIp() throws UnknownHostException {
		ServerProperties properties = new ServerProperties();
		properties.setAddress(InetAddress.getByAddress(new byte[] { 1, 2, 3, 4 }));

		ServerPropertiesHostLocator locator = new ServerPropertiesHostLocator(properties,
				"unknown",mockResolver("127.0.0.1"));

		assertThat(locator.locate(this.span).getAddress()).isEqualTo("1.2.3.4");
	}

	private LocalAdressResolver mockResolver(String address){
		LocalAdressResolver spy = Mockito.spy(new LocalAdressResolver());
		Mockito.when(spy.getLocalIp4AddressAsString()).thenReturn(address);
		return spy;
	}
}
