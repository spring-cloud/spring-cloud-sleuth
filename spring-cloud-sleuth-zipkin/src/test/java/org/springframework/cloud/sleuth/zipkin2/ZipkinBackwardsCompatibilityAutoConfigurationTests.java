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

package org.springframework.cloud.sleuth.zipkin2;

import java.util.List;

import org.junit.Test;
import zipkin2.Call;
import zipkin2.codec.BytesEncoder;
import zipkin2.codec.Encoding;
import zipkin2.reporter.AsyncReporter;
import zipkin2.reporter.InMemoryReporterMetrics;
import zipkin2.reporter.Reporter;
import zipkin2.reporter.ReporterMetrics;
import zipkin2.reporter.Sender;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.sleuth.autoconfig.TraceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Tim Ysewyn
 */
public class ZipkinBackwardsCompatibilityAutoConfigurationTests {

	private ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(
					ZipkinBackwardsCompatibilityAutoConfiguration.class,
					ZipkinAutoConfiguration.class, TraceAutoConfiguration.class));

	@Test
	public void shouldLoadBeans() {
		this.contextRunner.run(context -> {
			assertThat(context.getBean(ZipkinProperties.class)).isNotNull();
			assertThat(context.getBean(Reporter.class)).isNotNull();
			assertThat(context.getBean(BytesEncoder.class)).isNotNull();
			assertThat(context.getBean(ReporterMetrics.class))
					.isInstanceOf(InMemoryReporterMetrics.class);
		});
	}

	@Test
	public void shouldNotLoadBackwardsCompatibilityConfigWhenZipkinDisabled() {
		this.contextRunner.withPropertyValues("spring.zipkin.enabled=false")
				.run(context -> {
					assertThat(context.getBeansOfType(ZipkinProperties.class)).isEmpty();
					assertThat(context.getBeansOfType(BytesEncoder.class)).isEmpty();
					assertThat(context.getBean(ReporterMetrics.class)).isNotNull(); // TraceAutoConfiguration
					assertThat(context.getBean(Reporter.class)).isNotNull(); // noOpSpanReporter
				});
	}

	@Test
	public void shouldNotLoadBackwardsCompatibilityConfigWhenSleuthDisabled() {
		this.contextRunner.withPropertyValues("spring.sleuth.enabled=false")
				.run(context -> {
					assertThat(context.getBeansOfType(ZipkinProperties.class)).isEmpty();
					assertThat(context.getBeansOfType(BytesEncoder.class)).isEmpty();
					assertThat(context.getBeansOfType(ReporterMetrics.class)).isEmpty();
					assertThat(context.getBeansOfType(Reporter.class)).isEmpty();
				});
	}

	@Test
	public void shouldAllowOverridingSenderOnlyWithoutOverridingTheReporter() {
		this.contextRunner.withUserConfiguration(MyConfig.class).run(context -> {
			assertThat(context.getBean(ZipkinProperties.class)).isNotNull();
			assertThat(context.getBean(Reporter.class)).isInstanceOf(AsyncReporter.class);
			assertThat(context.getBean(Sender.class)).isInstanceOf(MySender.class);
			assertThat(context.getBean(BytesEncoder.class)).isNotNull();
			assertThat(context.getBean(ReporterMetrics.class))
					.isInstanceOf(InMemoryReporterMetrics.class);
		});
	}

	@Configuration
	protected static class MyConfig {

		@Bean(ZipkinAutoConfiguration.SENDER_BEAN_NAME)
		Sender mySender() {
			return new MySender();
		}

	}

	static class MySender extends Sender {

		@Override
		public Encoding encoding() {
			return Encoding.JSON;
		}

		@Override
		public int messageMaxBytes() {
			return Integer.MAX_VALUE;
		}

		@Override
		public int messageSizeInBytes(List<byte[]> encodedSpans) {
			return encoding().listSizeInBytes(encodedSpans);
		}

		@Override
		public Call<Void> sendSpans(List<byte[]> encodedSpans) {
			return Call.create(null);
		}

	}

}
