package sample;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.web.WebAppConfiguration;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = SampleSleuthApplication.class)
@WebAppConfiguration
public class SampleSleuthApplicationTests {

	@Test
	public void contextLoads() {
	}

}
