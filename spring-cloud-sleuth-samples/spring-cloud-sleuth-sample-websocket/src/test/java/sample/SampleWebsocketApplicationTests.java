package sample;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = SampleWebsocketApplication.class,
		properties="sample.zipkin.enabled=false",
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class SampleWebsocketApplicationTests {

	@Test
	public void contextLoads() {
	}

}
