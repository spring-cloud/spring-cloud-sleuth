package example;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.IntegrationTest;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import example.ZipkinStreamServerApplication;
import zipkin.SpanStore;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = ZipkinStreamServerApplication.class)
@IntegrationTest({ "server.port=0", "spring.datasource.initialize=true" })
@ActiveProfiles("test")
public class ZipkinServerApplicationTests {

	@Autowired
	private SpanStore store;

	@Test
	public void contextLoads() {
		int count = this.store.getServiceNames().size();
		assertEquals(0, count);
	}

}
