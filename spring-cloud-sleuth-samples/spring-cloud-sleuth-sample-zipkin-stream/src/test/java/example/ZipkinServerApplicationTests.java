package example;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import zipkin.storage.StorageComponent;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ZipkinStreamServerApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
		"spring.datasource.initialize=true" })
@ActiveProfiles("test")
public class ZipkinServerApplicationTests {

	@Autowired
	private StorageComponent storage;

	@Test
	public void contextLoads() {
		int count = this.storage.spanStore().getServiceNames().size();
		assertEquals(0, count);
	}

}
