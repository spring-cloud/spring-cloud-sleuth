/*
 * Copyright 2013-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package sample;

import java.util.Random;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

/**
 * @author Spencer Gibb
 * @author Dave Syer
 */
@RestController
public class SampleController  {

	@Autowired
	private RestTemplate restTemplate;
	private Random random = new Random();

	@RequestMapping("/")
	public String hi() throws InterruptedException {
		Thread.sleep(this.random.nextInt(1000));
		String s = this.restTemplate.getForObject("http://zipkin/hi2", String.class);
		return "hi/" + s;
	}

	@RequestMapping("/call")
	public String traced() {
		String s = this.restTemplate.getForObject("http://zipkin/call", String.class);
		return "call/" + s;
	}

}
