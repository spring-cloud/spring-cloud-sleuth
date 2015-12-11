package org.springframework.cloud.sleuth.zipkin.stream;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

@SpringBootApplication
@EnableZipkinStreamServer
public class ZipkinQueryServerApplication {

	public static void main(String[] args) throws Exception {
		new SpringApplicationBuilder(ZipkinQueryServerApplication.class)
				.properties("spring.config.name=zipkin-server").run(args);
	}

}
