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

package org.springframework.cloud.sleuth.instrument.grpc.stubs;

public final class HelloServiceOuterClass {

	static final com.google.protobuf.Descriptors.Descriptor internal_static_sample_grpc_HelloRequest_descriptor;
	static final com.google.protobuf.GeneratedMessageV3.FieldAccessorTable internal_static_sample_grpc_HelloRequest_fieldAccessorTable;
	static final com.google.protobuf.Descriptors.Descriptor internal_static_sample_grpc_HelloReply_descriptor;
	static final com.google.protobuf.GeneratedMessageV3.FieldAccessorTable internal_static_sample_grpc_HelloReply_fieldAccessorTable;

	private static com.google.protobuf.Descriptors.FileDescriptor descriptor;

	static {
		java.lang.String[] descriptorData = {
				"\n\022HelloService.proto\022\013sample.grpc\"\034\n\014Hel"
						+ "loRequest\022\014\n\004name\030\001 \001(\t\"\035\n\nHelloReply\022\017\n"
						+ "\007message\030\001 \001(\t2P\n\014HelloService\022@\n\010SayHel"
						+ "lo\022\031.sample.grpc.HelloRequest\032\027.sample.g"
						+ "rpc.HelloReply\"\000B\002P\001b\006proto3" };
		com.google.protobuf.Descriptors.FileDescriptor.InternalDescriptorAssigner assigner = new com.google.protobuf.Descriptors.FileDescriptor.InternalDescriptorAssigner() {
			@Override
			public com.google.protobuf.ExtensionRegistry assignDescriptors(
					com.google.protobuf.Descriptors.FileDescriptor root) {
				descriptor = root;
				return null;
			}
		};
		com.google.protobuf.Descriptors.FileDescriptor.internalBuildGeneratedFileFrom(
				descriptorData, new com.google.protobuf.Descriptors.FileDescriptor[] {},
				assigner);
		internal_static_sample_grpc_HelloRequest_descriptor = getDescriptor()
				.getMessageTypes().get(0);
		internal_static_sample_grpc_HelloRequest_fieldAccessorTable = new com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
				internal_static_sample_grpc_HelloRequest_descriptor,
				new java.lang.String[] { "Name", });
		internal_static_sample_grpc_HelloReply_descriptor = getDescriptor()
				.getMessageTypes().get(1);
		internal_static_sample_grpc_HelloReply_fieldAccessorTable = new com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
				internal_static_sample_grpc_HelloReply_descriptor,
				new java.lang.String[] { "Message", });
	}

	private HelloServiceOuterClass() {
	}

	public static void registerAllExtensions(
			com.google.protobuf.ExtensionRegistryLite registry) {
	}

	public static void registerAllExtensions(
			com.google.protobuf.ExtensionRegistry registry) {
		registerAllExtensions((com.google.protobuf.ExtensionRegistryLite) registry);
	}

	public static com.google.protobuf.Descriptors.FileDescriptor getDescriptor() {
		return descriptor;
	}

	// @@protoc_insertion_point(outer_class_scope)

}
