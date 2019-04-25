/*
 * Copyright 2018-2019 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.grpc;

import java.util.List;
import java.util.concurrent.TimeUnit;

import brave.sampler.Sampler;
import io.grpc.ClientInterceptor;
import io.grpc.ManagedChannel;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.lognet.springboot.grpc.GRpcServerBuilderConfigurer;
import org.lognet.springboot.grpc.GRpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zipkin2.Span;
import zipkin2.reporter.Reporter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.sleuth.instrument.grpc.stubs.HelloReply;
import org.springframework.cloud.sleuth.instrument.grpc.stubs.HelloRequest;
import org.springframework.cloud.sleuth.instrument.grpc.stubs.HelloServiceGrpc;
import org.springframework.cloud.sleuth.instrument.grpc.stubs.HelloServiceGrpc.HelloServiceBlockingStub;
import org.springframework.cloud.sleuth.instrument.grpc.stubs.HelloServiceGrpc.HelloServiceImplBase;
import org.springframework.cloud.sleuth.util.ArrayListSpanReporter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * This integration testing class starts an in-process gRPC server and calls that server
 * via the client.
 *
 * This class uses stubs and skeletons that were generated originally by the gRPC maven
 * plugin and copied into a "stubs" sub-package.
 *
 * @author Tyler Van Gorder
 */
@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = GrpcTracingIntegrationTests.TestConfiguration.class,
		webEnvironment = SpringBootTest.WebEnvironment.NONE,
		properties = { "grpc.enabled=false", "grpc.inProcessServerName=testServer" })
@DirtiesContext
public class GrpcTracingIntegrationTests {

	@Autowired
	SpringAwareManagedChannelBuilder clientManagedChannelBuilder;

	@Autowired
	ArrayListSpanReporter reporter;

	@Before
	public void beforeTest() {
		this.reporter.clear();
	}

	@After
	public void afterTest() {
		this.reporter.clear();
	}

	@Test
	public void integrationTest() throws Exception {
		ManagedChannel inProcessManagedChannel = this.clientManagedChannelBuilder
				.inProcessChannelBuilder("testServer").directExecutor().build();

		HelloServiceGrpcClient client = new HelloServiceGrpcClient(
				inProcessManagedChannel);

		assertThat(client.sayHello("Testy McTest Face"))
				.isEqualTo("Hello Testy McTest Face");
		List<Span> spans = this.reporter.getSpans();
		assertThat(spans).hasSize(2);
		assertThat(spans.get(0).kind()).isEqualTo(Span.Kind.SERVER);
		assertThat(spans.get(1).kind()).isEqualTo(Span.Kind.CLIENT);

		// ManagedChannel does not implement Closeable...
		inProcessManagedChannel.shutdownNow();
	}

	@Test
	public void channelBuilderFromAddress() {
		// Simple test to make sure the interceptor is added to the builder.
		this.clientManagedChannelBuilder.forAddress("test", 1234);
		@SuppressWarnings("unchecked")
		List<ClientInterceptor> clientInterceptors = (List<ClientInterceptor>) ReflectionTestUtils
				.getField(this.clientManagedChannelBuilder, "customizers");
		assertThat(clientInterceptors).hasSize(1);
	}

	@Test
	public void channelBuilderFromTarget() {
		// Simple test to make sure the interceptor is added to the builder.
		this.clientManagedChannelBuilder.forTarget("test");
		@SuppressWarnings("unchecked")
		List<ClientInterceptor> clientInterceptors = (List<ClientInterceptor>) ReflectionTestUtils
				.getField(this.clientManagedChannelBuilder, "customizers");
		assertThat(clientInterceptors).hasSize(1);
	}

	public interface HelloServiceClient {

		String sayHello(String name) throws Exception;

	}

	@Configuration
	@EnableAutoConfiguration
	@Import(HelloGrpcService.class)
	public static class TestConfiguration {

		@Bean
		Sampler alwaysSampler() {
			return Sampler.ALWAYS_SAMPLE;
		}

		@Bean
		Reporter<zipkin2.Span> reporter() {
			return new ArrayListSpanReporter();

		}

		@Bean
		GRpcServerBuilderConfigurer serverBuilderConfigurer() {
			return new TestGrpcConfig();
		}

	}

	public static class TestGrpcConfig extends GRpcServerBuilderConfigurer {

		@Override
		public void configure(ServerBuilder<?> serverBuilder) {
			serverBuilder.directExecutor();
		}

	}

	@GRpcService
	public static class HelloGrpcService extends HelloServiceImplBase {

		private Logger logger = LoggerFactory.getLogger(HelloGrpcService.class);

		@Override
		public void sayHello(HelloRequest request,
				StreamObserver<HelloReply> responseObserver) {
			String message = "Hello " + request.getName();
			this.logger.debug("In the grpc server stub.");
			HelloReply reply = HelloReply.newBuilder().setMessage(message).build();
			responseObserver.onNext(reply);
			responseObserver.onCompleted();
		}

	}

	public static class HelloServiceGrpcClient implements HelloServiceClient {

		private ManagedChannel managedChannel;

		public HelloServiceGrpcClient(ManagedChannel managedChannel) {
			this.managedChannel = managedChannel;
		}

		/*
		 * (non-Javadoc)
		 *
		 * @see sample.HelloServiceClient#sayHello(java.lang.String)
		 */
		@Override
		public String sayHello(String name) throws Exception {

			HelloServiceBlockingStub stub = HelloServiceGrpc
					.newBlockingStub(this.managedChannel)
					.withDeadlineAfter(3, TimeUnit.SECONDS);
			HelloReply reply = stub
					.sayHello(HelloRequest.newBuilder().setName(name).build());
			return reply.getMessage();

		}

	}

}
