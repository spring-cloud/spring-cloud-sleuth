/*
 * Copyright 2013-2019 the original author or authors.
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

/**
 * <pre>
 * The response message containing the greetings.
 * </pre>
 *
 * Protobuf type {@code HelloReply}
 *
 * @author Tyler Van Gorder
 */
public final class HelloReply extends com.google.protobuf.GeneratedMessageV3 implements
		// @@protoc_insertion_point(message_implements:HelloReply)
		HelloReplyOrBuilder {

	public static final int MESSAGE_FIELD_NUMBER = 1;

	private static final long serialVersionUID = 0L;

	// @@protoc_insertion_point(class_scope:HelloReply)
	private static final HelloReply DEFAULT_INSTANCE;

	private static final com.google.protobuf.Parser<HelloReply> PARSER = new com.google.protobuf.AbstractParser<HelloReply>() {
		@Override
		public HelloReply parsePartialFrom(com.google.protobuf.CodedInputStream input,
				com.google.protobuf.ExtensionRegistryLite extensionRegistry)
				throws com.google.protobuf.InvalidProtocolBufferException {
			return new HelloReply(input, extensionRegistry);
		}
	};

	static {
		DEFAULT_INSTANCE = new HelloReply();
	}

	private volatile java.lang.Object message_;

	private byte memoizedIsInitialized = -1;

	// Use HelloReply.newBuilder() to construct.
	private HelloReply(com.google.protobuf.GeneratedMessageV3.Builder<?> builder) {
		super(builder);
	}

	private HelloReply() {
		this.message_ = "";
	}

	private HelloReply(com.google.protobuf.CodedInputStream input,
			com.google.protobuf.ExtensionRegistryLite extensionRegistry)
			throws com.google.protobuf.InvalidProtocolBufferException {
		this();
		if (extensionRegistry == null) {
			throw new java.lang.NullPointerException();
		}
		int mutable_bitField0_ = 0;
		com.google.protobuf.UnknownFieldSet.Builder unknownFields = com.google.protobuf.UnknownFieldSet
				.newBuilder();
		try {
			boolean done = false;
			while (!done) {
				int tag = input.readTag();
				switch (tag) {
				case 0:
					done = true;
					break;
				default:
					if (!parseUnknownFieldProto3(input, unknownFields, extensionRegistry,
							tag)) {
						done = true;
					}
					break;
				case 10:
					String s = input.readStringRequireUtf8();
					this.message_ = s;
					break;
				}
			}
		}
		catch (com.google.protobuf.InvalidProtocolBufferException e) {
			throw e.setUnfinishedMessage(this);
		}
		catch (java.io.IOException e) {
			throw new com.google.protobuf.InvalidProtocolBufferException(e)
					.setUnfinishedMessage(this);
		}
		finally {
			this.unknownFields = unknownFields.build();
			makeExtensionsImmutable();
		}
	}

	public static final com.google.protobuf.Descriptors.Descriptor getDescriptor() {
		return HelloServiceOuterClass.internal_static_sample_grpc_HelloReply_descriptor;
	}

	public static HelloReply parseFrom(java.nio.ByteBuffer data)
			throws com.google.protobuf.InvalidProtocolBufferException {
		return PARSER.parseFrom(data);
	}

	public static HelloReply parseFrom(java.nio.ByteBuffer data,
			com.google.protobuf.ExtensionRegistryLite extensionRegistry)
			throws com.google.protobuf.InvalidProtocolBufferException {
		return PARSER.parseFrom(data, extensionRegistry);
	}

	public static HelloReply parseFrom(com.google.protobuf.ByteString data)
			throws com.google.protobuf.InvalidProtocolBufferException {
		return PARSER.parseFrom(data);
	}

	public static HelloReply parseFrom(com.google.protobuf.ByteString data,
			com.google.protobuf.ExtensionRegistryLite extensionRegistry)
			throws com.google.protobuf.InvalidProtocolBufferException {
		return PARSER.parseFrom(data, extensionRegistry);
	}

	public static HelloReply parseFrom(byte[] data)
			throws com.google.protobuf.InvalidProtocolBufferException {
		return PARSER.parseFrom(data);
	}

	public static HelloReply parseFrom(byte[] data,
			com.google.protobuf.ExtensionRegistryLite extensionRegistry)
			throws com.google.protobuf.InvalidProtocolBufferException {
		return PARSER.parseFrom(data, extensionRegistry);
	}

	public static HelloReply parseFrom(java.io.InputStream input)
			throws java.io.IOException {
		return com.google.protobuf.GeneratedMessageV3.parseWithIOException(PARSER, input);
	}

	public static HelloReply parseFrom(java.io.InputStream input,
			com.google.protobuf.ExtensionRegistryLite extensionRegistry)
			throws java.io.IOException {
		return com.google.protobuf.GeneratedMessageV3.parseWithIOException(PARSER, input,
				extensionRegistry);
	}

	public static HelloReply parseDelimitedFrom(java.io.InputStream input)
			throws java.io.IOException {
		return com.google.protobuf.GeneratedMessageV3
				.parseDelimitedWithIOException(PARSER, input);
	}

	public static HelloReply parseDelimitedFrom(java.io.InputStream input,
			com.google.protobuf.ExtensionRegistryLite extensionRegistry)
			throws java.io.IOException {
		return com.google.protobuf.GeneratedMessageV3
				.parseDelimitedWithIOException(PARSER, input, extensionRegistry);
	}

	public static HelloReply parseFrom(com.google.protobuf.CodedInputStream input)
			throws java.io.IOException {
		return com.google.protobuf.GeneratedMessageV3.parseWithIOException(PARSER, input);
	}

	public static HelloReply parseFrom(com.google.protobuf.CodedInputStream input,
			com.google.protobuf.ExtensionRegistryLite extensionRegistry)
			throws java.io.IOException {
		return com.google.protobuf.GeneratedMessageV3.parseWithIOException(PARSER, input,
				extensionRegistry);
	}

	public static Builder newBuilder() {
		return DEFAULT_INSTANCE.toBuilder();
	}

	public static Builder newBuilder(HelloReply prototype) {
		return DEFAULT_INSTANCE.toBuilder().mergeFrom(prototype);
	}

	public static HelloReply getDefaultInstance() {
		return DEFAULT_INSTANCE;
	}

	public static com.google.protobuf.Parser<HelloReply> parser() {
		return PARSER;
	}

	@java.lang.Override
	public final com.google.protobuf.UnknownFieldSet getUnknownFields() {
		return this.unknownFields;
	}

	@Override
	protected com.google.protobuf.GeneratedMessageV3.FieldAccessorTable internalGetFieldAccessorTable() {
		return HelloServiceOuterClass.internal_static_sample_grpc_HelloReply_fieldAccessorTable
				.ensureFieldAccessorsInitialized(HelloReply.class,
						HelloReply.Builder.class);
	}

	/**
	 * <code>string message = 1;</code>
	 */
	@Override
	public java.lang.String getMessage() {
		java.lang.Object ref = this.message_;
		if (ref instanceof java.lang.String) {
			return (java.lang.String) ref;
		}
		else {
			com.google.protobuf.ByteString bs = (com.google.protobuf.ByteString) ref;
			java.lang.String s = bs.toStringUtf8();
			this.message_ = s;
			return s;
		}
	}

	/**
	 * <code>string message = 1;</code>
	 */
	@Override
	public com.google.protobuf.ByteString getMessageBytes() {
		java.lang.Object ref = this.message_;
		if (ref instanceof java.lang.String) {
			com.google.protobuf.ByteString b = com.google.protobuf.ByteString
					.copyFromUtf8((java.lang.String) ref);
			this.message_ = b;
			return b;
		}
		else {
			return (com.google.protobuf.ByteString) ref;
		}
	}

	@Override
	public final boolean isInitialized() {
		byte isInitialized = this.memoizedIsInitialized;
		if (isInitialized == 1) {
			return true;
		}
		if (isInitialized == 0) {
			return false;
		}

		this.memoizedIsInitialized = 1;
		return true;
	}

	@Override
	public void writeTo(com.google.protobuf.CodedOutputStream output)
			throws java.io.IOException {
		if (!getMessageBytes().isEmpty()) {
			com.google.protobuf.GeneratedMessageV3.writeString(output, 1, this.message_);
		}
		this.unknownFields.writeTo(output);
	}

	@Override
	public int getSerializedSize() {
		int size = this.memoizedSize;
		if (size != -1) {
			return size;
		}

		size = 0;
		if (!getMessageBytes().isEmpty()) {
			size += com.google.protobuf.GeneratedMessageV3.computeStringSize(1,
					this.message_);
		}
		size += this.unknownFields.getSerializedSize();
		this.memoizedSize = size;
		return size;
	}

	@java.lang.Override
	public boolean equals(final java.lang.Object obj) {
		if (obj == this) {
			return true;
		}
		if (!(obj instanceof HelloReply)) {
			return super.equals(obj);
		}
		HelloReply other = (HelloReply) obj;

		boolean result = true;
		result = result && getMessage().equals(other.getMessage());
		result = result && this.unknownFields.equals(other.unknownFields);
		return result;
	}

	@java.lang.Override
	public int hashCode() {
		if (this.memoizedHashCode != 0) {
			return this.memoizedHashCode;
		}
		int hash = 41;
		hash = (19 * hash) + getDescriptor().hashCode();
		hash = (37 * hash) + MESSAGE_FIELD_NUMBER;
		hash = (53 * hash) + getMessage().hashCode();
		hash = (29 * hash) + this.unknownFields.hashCode();
		this.memoizedHashCode = hash;
		return hash;
	}

	@Override
	public Builder newBuilderForType() {
		return newBuilder();
	}

	@Override
	public Builder toBuilder() {
		return this == DEFAULT_INSTANCE ? new Builder() : new Builder().mergeFrom(this);
	}

	@java.lang.Override
	protected Builder newBuilderForType(
			com.google.protobuf.GeneratedMessageV3.BuilderParent parent) {
		Builder builder = new Builder(parent);
		return builder;
	}

	@java.lang.Override
	public com.google.protobuf.Parser<HelloReply> getParserForType() {
		return PARSER;
	}

	@Override
	public HelloReply getDefaultInstanceForType() {
		return DEFAULT_INSTANCE;
	}

	/**
	 * <pre>
	 * The response message containing the greetings
	 * </pre>
	 *
	 * Protobuf type {@code HelloReply}
	 */
	public static final class Builder
			extends com.google.protobuf.GeneratedMessageV3.Builder<Builder> implements
			// @@protoc_insertion_point(builder_implements:HelloReply)
			HelloReplyOrBuilder {

		private java.lang.Object message_ = "";

		// Construct using HelloReply.newBuilder()
		private Builder() {
			maybeForceBuilderInitialization();
		}

		private Builder(com.google.protobuf.GeneratedMessageV3.BuilderParent parent) {
			super(parent);
			maybeForceBuilderInitialization();
		}

		public static final com.google.protobuf.Descriptors.Descriptor getDescriptor() {
			return HelloServiceOuterClass.internal_static_sample_grpc_HelloReply_descriptor;
		}

		@Override
		protected com.google.protobuf.GeneratedMessageV3.FieldAccessorTable internalGetFieldAccessorTable() {
			return HelloServiceOuterClass.internal_static_sample_grpc_HelloReply_fieldAccessorTable
					.ensureFieldAccessorsInitialized(HelloReply.class,
							HelloReply.Builder.class);
		}

		private void maybeForceBuilderInitialization() {
			if (com.google.protobuf.GeneratedMessageV3.alwaysUseFieldBuilders) {
			}
		}

		@Override
		public Builder clear() {
			super.clear();
			this.message_ = "";

			return this;
		}

		@Override
		public com.google.protobuf.Descriptors.Descriptor getDescriptorForType() {
			return HelloServiceOuterClass.internal_static_sample_grpc_HelloReply_descriptor;
		}

		@Override
		public HelloReply getDefaultInstanceForType() {
			return HelloReply.getDefaultInstance();
		}

		@Override
		public HelloReply build() {
			HelloReply result = buildPartial();
			if (!result.isInitialized()) {
				throw newUninitializedMessageException(result);
			}
			return result;
		}

		@Override
		public HelloReply buildPartial() {
			HelloReply result = new HelloReply(this);
			result.message_ = this.message_;
			onBuilt();
			return result;
		}

		@Override
		public Builder clone() {
			return super.clone();
		}

		@Override
		public Builder setField(com.google.protobuf.Descriptors.FieldDescriptor field,
				java.lang.Object value) {
			return super.setField(field, value);
		}

		@Override
		public Builder clearField(com.google.protobuf.Descriptors.FieldDescriptor field) {
			return super.clearField(field);
		}

		@Override
		public Builder clearOneof(com.google.protobuf.Descriptors.OneofDescriptor oneof) {
			return super.clearOneof(oneof);
		}

		@Override
		public Builder setRepeatedField(
				com.google.protobuf.Descriptors.FieldDescriptor field, int index,
				java.lang.Object value) {
			return super.setRepeatedField(field, index, value);
		}

		@Override
		public Builder addRepeatedField(
				com.google.protobuf.Descriptors.FieldDescriptor field,
				java.lang.Object value) {
			return super.addRepeatedField(field, value);
		}

		@Override
		public Builder mergeFrom(com.google.protobuf.Message other) {
			if (other instanceof HelloReply) {
				return mergeFrom((HelloReply) other);
			}
			else {
				super.mergeFrom(other);
				return this;
			}
		}

		public Builder mergeFrom(HelloReply other) {
			if (other == HelloReply.getDefaultInstance()) {
				return this;
			}
			if (!other.getMessage().isEmpty()) {
				this.message_ = other.message_;
				onChanged();
			}
			this.mergeUnknownFields(other.unknownFields);
			onChanged();
			return this;
		}

		@Override
		public final boolean isInitialized() {
			return true;
		}

		@Override
		public Builder mergeFrom(com.google.protobuf.CodedInputStream input,
				com.google.protobuf.ExtensionRegistryLite extensionRegistry)
				throws java.io.IOException {
			HelloReply parsedMessage = null;
			try {
				parsedMessage = PARSER.parsePartialFrom(input, extensionRegistry);
			}
			catch (com.google.protobuf.InvalidProtocolBufferException e) {
				parsedMessage = (HelloReply) e.getUnfinishedMessage();
				throw e.unwrapIOException();
			}
			finally {
				if (parsedMessage != null) {
					mergeFrom(parsedMessage);
				}
			}
			return this;
		}

		/**
		 * <code>string message = 1;</code>
		 */
		@Override
		public java.lang.String getMessage() {
			java.lang.Object ref = this.message_;
			if (!(ref instanceof java.lang.String)) {
				com.google.protobuf.ByteString bs = (com.google.protobuf.ByteString) ref;
				java.lang.String s = bs.toStringUtf8();
				this.message_ = s;
				return s;
			}
			else {
				return (java.lang.String) ref;
			}
		}

		/**
		 * <code>string message = 1;</code>
		 */
		public Builder setMessage(java.lang.String value) {
			if (value == null) {
				throw new NullPointerException();
			}

			this.message_ = value;
			onChanged();
			return this;
		}

		/**
		 * <code>string message = 1;</code>
		 */
		@Override
		public com.google.protobuf.ByteString getMessageBytes() {
			java.lang.Object ref = this.message_;
			if (ref instanceof String) {
				com.google.protobuf.ByteString b = com.google.protobuf.ByteString
						.copyFromUtf8((java.lang.String) ref);
				this.message_ = b;
				return b;
			}
			else {
				return (com.google.protobuf.ByteString) ref;
			}
		}

		/**
		 * <code>string message = 1;</code>
		 */
		public Builder setMessageBytes(com.google.protobuf.ByteString value) {
			if (value == null) {
				throw new NullPointerException();
			}
			checkByteStringIsUtf8(value);

			this.message_ = value;
			onChanged();
			return this;
		}

		/**
		 * <code>string message = 1;</code>
		 */
		public Builder clearMessage() {

			this.message_ = getDefaultInstance().getMessage();
			onChanged();
			return this;
		}

		@Override
		public final Builder setUnknownFields(
				final com.google.protobuf.UnknownFieldSet unknownFields) {
			return super.setUnknownFieldsProto3(unknownFields);
		}

		@Override
		public final Builder mergeUnknownFields(
				final com.google.protobuf.UnknownFieldSet unknownFields) {
			return super.mergeUnknownFields(unknownFields);
		}

		// @@protoc_insertion_point(builder_scope:HelloReply)

	}

}
