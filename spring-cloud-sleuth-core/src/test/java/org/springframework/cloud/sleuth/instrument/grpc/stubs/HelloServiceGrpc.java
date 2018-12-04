/*
 * Copyright 2018 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.grpc.stubs;

import static io.grpc.MethodDescriptor.generateFullMethodName;
import static io.grpc.stub.ClientCalls.asyncUnaryCall;
import static io.grpc.stub.ClientCalls.blockingUnaryCall;
import static io.grpc.stub.ClientCalls.futureUnaryCall;
import static io.grpc.stub.ServerCalls.asyncUnaryCall;
import static io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall;

/**
 * <pre>
 * The Hello service definition.
 * </pre>
 */
@javax.annotation.Generated(value = "by gRPC proto compiler (version 1.15.1)", comments = "Source: HelloService.proto")
public final class HelloServiceGrpc {

	private HelloServiceGrpc() {
	}

	public static final String SERVICE_NAME = "HelloService";

	// Static method descriptors that strictly reflect the proto.
	private static volatile io.grpc.MethodDescriptor<HelloRequest, HelloReply> getSayHelloMethod;

	@io.grpc.stub.annotations.RpcMethod(fullMethodName = SERVICE_NAME + '/'
			+ "SayHello", requestType = HelloRequest.class, responseType = HelloReply.class, methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
	public static io.grpc.MethodDescriptor<HelloRequest, HelloReply> getSayHelloMethod() {
		io.grpc.MethodDescriptor<HelloRequest, HelloReply> getSayHelloMethod;
		if ((getSayHelloMethod = HelloServiceGrpc.getSayHelloMethod) == null) {
			synchronized (HelloServiceGrpc.class) {
				if ((getSayHelloMethod = HelloServiceGrpc.getSayHelloMethod) == null) {
					HelloServiceGrpc.getSayHelloMethod = getSayHelloMethod = io.grpc.MethodDescriptor
							.<HelloRequest, HelloReply>newBuilder()
							.setType(io.grpc.MethodDescriptor.MethodType.UNARY)
							.setFullMethodName(
									generateFullMethodName("HelloService", "SayHello"))
							.setSampledToLocalTracing(true)
							.setRequestMarshaller(io.grpc.protobuf.ProtoUtils
									.marshaller(HelloRequest.getDefaultInstance()))
							.setResponseMarshaller(io.grpc.protobuf.ProtoUtils
									.marshaller(HelloReply.getDefaultInstance()))
							.setSchemaDescriptor(
									new HelloServiceMethodDescriptorSupplier("SayHello"))
							.build();
				}
			}
		}
		return getSayHelloMethod;
	}

	/**
	 * Creates a new async stub that supports all call types for the service
	 */
	public static HelloServiceStub newStub(io.grpc.Channel channel) {
		return new HelloServiceStub(channel);
	}

	/**
	 * Creates a new blocking-style stub that supports unary and streaming output calls on
	 * the service
	 */
	public static HelloServiceBlockingStub newBlockingStub(io.grpc.Channel channel) {
		return new HelloServiceBlockingStub(channel);
	}

	/**
	 * Creates a new ListenableFuture-style stub that supports unary calls on the service
	 */
	public static HelloServiceFutureStub newFutureStub(io.grpc.Channel channel) {
		return new HelloServiceFutureStub(channel);
	}

	/**
	 * <pre>
	 * The Hello service definition.
	 * </pre>
	 */
	public static abstract class HelloServiceImplBase implements io.grpc.BindableService {

		/**
		 * <pre>
		 * Sends a greeting
		 * </pre>
		 */
		public void sayHello(HelloRequest request,
				io.grpc.stub.StreamObserver<HelloReply> responseObserver) {
			asyncUnimplementedUnaryCall(getSayHelloMethod(), responseObserver);
		}

		@java.lang.Override
		public final io.grpc.ServerServiceDefinition bindService() {
			return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
					.addMethod(getSayHelloMethod(),
							asyncUnaryCall(new MethodHandlers<HelloRequest, HelloReply>(
									this, METHODID_SAY_HELLO)))
					.build();
		}

	}

	/**
	 * <pre>
	 * The Hello service definition.
	 * </pre>
	 */
	public static final class HelloServiceStub
			extends io.grpc.stub.AbstractStub<HelloServiceStub> {

		private HelloServiceStub(io.grpc.Channel channel) {
			super(channel);
		}

		private HelloServiceStub(io.grpc.Channel channel,
				io.grpc.CallOptions callOptions) {
			super(channel, callOptions);
		}

		@java.lang.Override
		protected HelloServiceStub build(io.grpc.Channel channel,
				io.grpc.CallOptions callOptions) {
			return new HelloServiceStub(channel, callOptions);
		}

		/**
		 * <pre>
		 * Sends a greeting
		 * </pre>
		 */
		public void sayHello(HelloRequest request,
				io.grpc.stub.StreamObserver<HelloReply> responseObserver) {
			asyncUnaryCall(getChannel().newCall(getSayHelloMethod(), getCallOptions()),
					request, responseObserver);
		}

	}

	/**
	 * <pre>
	 * The Hello service definition.
	 * </pre>
	 */
	public static final class HelloServiceBlockingStub
			extends io.grpc.stub.AbstractStub<HelloServiceBlockingStub> {

		private HelloServiceBlockingStub(io.grpc.Channel channel) {
			super(channel);
		}

		private HelloServiceBlockingStub(io.grpc.Channel channel,
				io.grpc.CallOptions callOptions) {
			super(channel, callOptions);
		}

		@java.lang.Override
		protected HelloServiceBlockingStub build(io.grpc.Channel channel,
				io.grpc.CallOptions callOptions) {
			return new HelloServiceBlockingStub(channel, callOptions);
		}

		/**
		 * <pre>
		 * Sends a greeting
		 * </pre>
		 */
		public HelloReply sayHello(HelloRequest request) {
			return blockingUnaryCall(getChannel(), getSayHelloMethod(), getCallOptions(),
					request);
		}

	}

	/**
	 * <pre>
	 * The Hello service definition.
	 * </pre>
	 */
	public static final class HelloServiceFutureStub
			extends io.grpc.stub.AbstractStub<HelloServiceFutureStub> {

		private HelloServiceFutureStub(io.grpc.Channel channel) {
			super(channel);
		}

		private HelloServiceFutureStub(io.grpc.Channel channel,
				io.grpc.CallOptions callOptions) {
			super(channel, callOptions);
		}

		@java.lang.Override
		protected HelloServiceFutureStub build(io.grpc.Channel channel,
				io.grpc.CallOptions callOptions) {
			return new HelloServiceFutureStub(channel, callOptions);
		}

		/**
		 * <pre>
		 * Sends a greeting
		 * </pre>
		 */
		public com.google.common.util.concurrent.ListenableFuture<HelloReply> sayHello(
				HelloRequest request) {
			return futureUnaryCall(
					getChannel().newCall(getSayHelloMethod(), getCallOptions()), request);
		}

	}

	private static final int METHODID_SAY_HELLO = 0;

	private static final class MethodHandlers<Req, Resp>
			implements io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
			io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
			io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
			io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {

		private final HelloServiceImplBase serviceImpl;

		private final int methodId;

		MethodHandlers(HelloServiceImplBase serviceImpl, int methodId) {
			this.serviceImpl = serviceImpl;
			this.methodId = methodId;
		}

		@java.lang.Override
		@java.lang.SuppressWarnings("unchecked")
		public void invoke(Req request,
				io.grpc.stub.StreamObserver<Resp> responseObserver) {
			switch (this.methodId) {
			case METHODID_SAY_HELLO:
				this.serviceImpl.sayHello((HelloRequest) request,
						(io.grpc.stub.StreamObserver<HelloReply>) responseObserver);
				break;
			default:
				throw new AssertionError();
			}
		}

		@java.lang.Override
		@java.lang.SuppressWarnings("unchecked")
		public io.grpc.stub.StreamObserver<Req> invoke(
				io.grpc.stub.StreamObserver<Resp> responseObserver) {
			switch (this.methodId) {
			default:
				throw new AssertionError();
			}
		}

	}

	private static abstract class HelloServiceBaseDescriptorSupplier
			implements io.grpc.protobuf.ProtoFileDescriptorSupplier,
			io.grpc.protobuf.ProtoServiceDescriptorSupplier {

		HelloServiceBaseDescriptorSupplier() {
		}

		@java.lang.Override
		public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
			return HelloServiceOuterClass.getDescriptor();
		}

		@java.lang.Override
		public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
			return getFileDescriptor().findServiceByName("HelloService");
		}

	}

	private static final class HelloServiceFileDescriptorSupplier
			extends HelloServiceBaseDescriptorSupplier {

		HelloServiceFileDescriptorSupplier() {
		}

	}

	private static final class HelloServiceMethodDescriptorSupplier
			extends HelloServiceBaseDescriptorSupplier
			implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {

		private final String methodName;

		HelloServiceMethodDescriptorSupplier(String methodName) {
			this.methodName = methodName;
		}

		@java.lang.Override
		public com.google.protobuf.Descriptors.MethodDescriptor getMethodDescriptor() {
			return getServiceDescriptor().findMethodByName(this.methodName);
		}

	}

	private static volatile io.grpc.ServiceDescriptor serviceDescriptor;

	public static io.grpc.ServiceDescriptor getServiceDescriptor() {
		io.grpc.ServiceDescriptor result = serviceDescriptor;
		if (result == null) {
			synchronized (HelloServiceGrpc.class) {
				result = serviceDescriptor;
				if (result == null) {
					serviceDescriptor = result = io.grpc.ServiceDescriptor
							.newBuilder(SERVICE_NAME)
							.setSchemaDescriptor(new HelloServiceFileDescriptorSupplier())
							.addMethod(getSayHelloMethod()).build();
				}
			}
		}
		return result;
	}

}
