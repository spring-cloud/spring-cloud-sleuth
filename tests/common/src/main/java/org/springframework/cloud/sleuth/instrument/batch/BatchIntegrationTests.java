/*
 * Copyright 2013-2021 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.batch;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.exporter.FinishedSpan;
import org.springframework.cloud.sleuth.test.TestSpanHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;

import static org.assertj.core.api.BDDAssertions.then;

@ContextConfiguration(classes = BatchIntegrationTests.TestConfig.class)
public abstract class BatchIntegrationTests {

	private static final Log log = LogFactory.getLog(BatchIntegrationTests.class);

	@Autowired
	TestSpanHandler spans;

	@Autowired
	Tracer tracer;

	@Autowired
	StepBuilderFactory stepBuilderFactory;

	@Autowired
	JobBuilderFactory jobBuilderFactory;

	@Autowired
	JobLauncher jobLauncher;

	@BeforeEach
	public void setup() {
		this.spans.clear();
	}

	@Test
	public void should_pass_tracing_information_when_using_batch() throws Exception {
		AtomicReference<Span> spanFromTasklet = new AtomicReference<>();
		Job job = this.jobBuilderFactory.get("myJob")
				.start(this.stepBuilderFactory.get("myTask").tasklet((stepContribution, chunkContext) -> {
					log.info("Hello");
					spanFromTasklet.set(this.tracer.currentSpan());
					return RepeatStatus.FINISHED;
				}).build()).build();

		JobExecution jobExecution = this.jobLauncher.run(job, new JobParameters());

		then(jobExecution.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode());
		then(spanFromTasklet.get()).isNotNull();
		List<FinishedSpan> spans = this.spans.reportedSpans();
		then(spans).hasSize(2);
		then(spans.stream().map(FinishedSpan::getTraceId).collect(Collectors.toSet())).hasSize(1);
		then(spans.get(0).getName()).isEqualTo("myTask");
		then(spans.get(1).getName()).isEqualTo("myJob");
		then(this.tracer.currentSpan()).isNull();
	}

	@Configuration(proxyBeanMethods = false)
	@EnableAutoConfiguration
	@EnableBatchProcessing
	public static class TestConfig {

	}

}
