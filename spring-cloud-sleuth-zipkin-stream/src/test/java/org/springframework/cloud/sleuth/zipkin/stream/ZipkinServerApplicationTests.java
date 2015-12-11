package org.springframework.cloud.sleuth.zipkin.stream;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.IntegrationTest;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.cloud.sleuth.zipkin.stream.ZipkinQueryServerApplication;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = ZipkinQueryServerApplication.class)
@IntegrationTest({ "server.port=0", "spring.datasource.initialize=true" })
@ActiveProfiles("test")
public class ZipkinServerApplicationTests {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	public void contextLoads() {
		int count = this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM zipkin_spans",
				Integer.class);
		assertEquals(0, count);
	}

}
