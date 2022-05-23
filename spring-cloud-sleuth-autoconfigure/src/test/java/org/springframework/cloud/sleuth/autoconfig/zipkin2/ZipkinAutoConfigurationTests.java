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

package org.springframework.cloud.sleuth.autoconfig.zipkin2;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import zipkin2.Call;
import zipkin2.CheckResult;
import zipkin2.codec.Encoding;
import zipkin2.reporter.Sender;

import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.BDDAssertions.then;
import static org.awaitility.Awaitility.await;

@ExtendWith(OutputCaptureExtension.class)
class ZipkinAutoConfigurationTests {

	ExecutorService service = Executors.newSingleThreadExecutor();

	@AfterEach
	void clean() {
		this.service.shutdown();
	}

	@Test
	void shouldReturnShortTimeoutExceptionWhenRootCauseTimeout(CapturedOutput capture) {
		ZipkinAutoConfiguration.checkResult(service,
				new CustomSender(() -> CheckResult.failed(new RuntimeException(new TimeoutException("boom")))), 1L);

		await().untilAsserted(() -> then(capture.toString()).doesNotContain("CheckResult{ok=true, error=null}")
				.contains("CheckResult{ok=false, error=java.util.concurrent.TimeoutException: Timed out after 1ms}"));
	}

	@Test
	void shouldReturnOriginalExceptionWhenRootCauseNotTimeout(CapturedOutput capture) {
		ZipkinAutoConfiguration.checkResult(service,
				new CustomSender(() -> CheckResult.failed(new RuntimeException(new RuntimeException("boom")))), 1L);

		await().atMost(1, TimeUnit.SECONDS).untilAsserted(
				() -> then(capture.toString()).doesNotContain("CheckResult{ok=true, error=null}").contains(
						"CheckResult{ok=false, error=java.lang.RuntimeException: java.lang.RuntimeException: boom}"));
	}

	@Test
	void shouldReturnCheckResultWhenNoExceptionPresent(CapturedOutput capture) {
		ZipkinAutoConfiguration.checkResult(service, new CustomSender(() -> CheckResult.OK), 1L);

		await().atMost(1, TimeUnit.SECONDS)
				.untilAsserted(() -> then(capture.toString()).contains("CheckResult{ok=true, error=null}"));
	}

	@Test
	void shouldReturnExceptionWhenNoCheckResultPresent(CapturedOutput capture) {
		ZipkinAutoConfiguration.checkResult(service, new ExceptionThrowingSender(), 1L);

		await().atMost(1, TimeUnit.SECONDS).untilAsserted(() -> then(capture.toString()).contains(
				"CheckResult{ok=false, error=java.util.concurrent.CompletionException: java.lang.RuntimeException: boom}"));
	}

	private static final class ExceptionThrowingSender extends Sender {

		@Override
		public Encoding encoding() {
			return null;
		}

		@Override
		public int messageMaxBytes() {
			return 0;
		}

		@Override
		public int messageSizeInBytes(List<byte[]> list) {
			return 0;
		}

		@Override
		public Call<Void> sendSpans(List<byte[]> list) {
			return null;
		}

		@Override
		public CheckResult check() {
			throw new RuntimeException("boom");
		}

	}

	private static final class CustomSender extends Sender {

		private final Supplier<CheckResult> doSth;

		private CustomSender(Supplier<CheckResult> doSth) {
			this.doSth = doSth;
		}

		@Override
		public Encoding encoding() {
			return null;
		}

		@Override
		public int messageMaxBytes() {
			return 0;
		}

		@Override
		public int messageSizeInBytes(List<byte[]> list) {
			return 0;
		}

		@Override
		public Call<Void> sendSpans(List<byte[]> list) {
			return null;
		}

		@Override
		public CheckResult check() {
			return this.doSth.get();
		}

	}

}
