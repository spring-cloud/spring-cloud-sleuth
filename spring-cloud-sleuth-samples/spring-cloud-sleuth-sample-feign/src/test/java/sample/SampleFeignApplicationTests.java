package sample;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = SampleFeignApplication.class)
@TestPropertySource(properties = "sample.zipkin.enabled=false")
public class SampleFeignApplicationTests {

	@Test
	public void contextLoads() {
	}

}
