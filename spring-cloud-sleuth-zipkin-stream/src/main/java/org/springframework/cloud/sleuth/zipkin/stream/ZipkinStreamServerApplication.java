package org.springframework.cloud.sleuth.zipkin.stream;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

@SpringBootApplication
@EnableZipkinStreamServer
public class ZipkinStreamServerApplication {

	public static void main(String[] args) throws Exception {
		new SpringApplicationBuilder(ZipkinStreamServerApplication.class)
				.properties("spring.config.name=zipkin-server").run(args);
	}

}
