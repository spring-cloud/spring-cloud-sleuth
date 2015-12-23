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

import lombok.extern.slf4j.Slf4j;
import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
public class SampleRouteBuilder extends RouteBuilder {

    @Autowired
    private TraceProcessorFactory traceProcessorFactory;

    @Override
    public void configure() {

        // close route's spans on failure
        onCompletion()
                .log("completion")
                .process(traceProcessorFactory.closeSpansProcessor())
                .log("completed");

        // do two executions, so we have two traces
        from("timer:sampleTimer?period=1000&daemon=false&repeatCount=2")
                .setExchangePattern(ExchangePattern.InOut)
                .process(traceProcessorFactory.startSpanProcessor("foo"))
                .log("start foo span")
                .process(traceProcessorFactory.startSpanProcessor("bar"))
                .log("in bar span")
                .process(traceProcessorFactory.closeSpanProcessor())
                .log("end foo span")
                .to("mock:result");
        // ops - I didn't close the span!

    }
}
