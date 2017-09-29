package sample;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.web.WebAppConfiguration;

// TODO: Fix me
@RunWith(SpringRunner.class)
@SpringBootTest(classes = SampleSleuthApplication.class)
@WebAppConfiguration
@Ignore
public class SampleSleuthApplicationTests {

	@Test
	public void contextLoads() {
	}

}
