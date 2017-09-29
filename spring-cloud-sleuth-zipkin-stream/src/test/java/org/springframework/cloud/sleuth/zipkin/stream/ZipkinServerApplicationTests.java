package org.springframework.cloud.sleuth.zipkin.stream;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.cloud.sleuth.zipkin.stream.ZipkinServerApplicationTests.ZipkinStreamServerApplication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import zipkin.storage.StorageComponent;

import static org.junit.Assert.assertEquals;

// TODO: Fix me
@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = ZipkinStreamServerApplication.class, properties = {
		"spring.datasource.initialize=true" }, webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Ignore
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
