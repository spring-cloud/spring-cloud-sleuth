/*
 * Copyright 2013-2015 the original author or authors.
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

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.sleuth.Sampler;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@SpringBootApplication
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class SampleSleuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(SampleSleuthApplication.class, args);
    }

    @Bean
    public Sampler<?> defaultSampler() {
        return new AlwaysSampler();
    }

    @Bean
    public SampleRouteBuilder sampleRouteBuilder() {
        return new SampleRouteBuilder();
    }

    @Bean
    public TraceProcessorFactory traceProcessorFactory() {
        return new TraceProcessorFactory();
    }
}
