package org.springframework.cloud.sleuth.zipkin.stream;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.sleuth.zipkin.stream.ZipkinServerApplicationTests.ZipkinStreamServerApplication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import zipkin.storage.StorageComponent;

import static org.junit.Assert.assertEquals;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ZipkinStreamServerApplication.class,
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = { "spring.datasource.initialize=true" })
@ActiveProfiles("test")
public class ZipkinServerApplicationTests {

	@Autowired
	private StorageComponent storage;

	@Test
	public void contextLoads() {
		int count = this.storage.spanStore().getServiceNames().size();
		assertEquals(0, count);
	}

	@SpringBootApplication
	@EnableZipkinStreamServer
	public static class ZipkinStreamServerApplication {

		public static void main(String[] args) throws Exception {
			new SpringApplicationBuilder(ZipkinStreamServerApplication.class)
					.profiles("test").properties("spring.datasource.initialize=true")
					.run(args);
		}

	}
}
