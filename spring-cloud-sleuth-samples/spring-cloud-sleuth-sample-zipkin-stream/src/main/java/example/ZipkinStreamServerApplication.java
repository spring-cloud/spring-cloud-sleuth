package example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.sleuth.zipkin.stream.EnableZipkinStreamServer;

@SpringBootApplication
@EnableZipkinStreamServer
public class ZipkinStreamServerApplication {

	public static void main(String[] args) throws Exception {
		SpringApplication.run(ZipkinStreamServerApplication.class, args);
	}

}