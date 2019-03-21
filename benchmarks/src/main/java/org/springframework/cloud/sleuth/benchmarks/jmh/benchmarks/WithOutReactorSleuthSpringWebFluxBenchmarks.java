/*
 * Copyright 2013-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.cloud.sleuth.benchmarks.jmh.benchmarks;

import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * @author alvin
 */
public class WithOutReactorSleuthSpringWebFluxBenchmarks extends SpringWebFluxBenchmarks {

	@Override
	protected String[] runArgs() {
		return new String[]{"--spring.jmx.enabled=false",
				"--spring.application.name=defaultTraceContext",
				"--spring.sleuth.enabled=true",
				"--spring.sleuth.reactor.enabled=false"

		};
	}

	@Override
	protected void postSetUp() {
		super.postSetUp();
	}

	public static void main(String[] args) throws RunnerException {
		Options opt = new OptionsBuilder()
				.include(".*" + WithOutReactorSleuthSpringWebFluxBenchmarks.class.getSimpleName() + ".*")
				.build();

		new Runner(opt).run();
	}
}
