/*
 * Copyright 2013-2017 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.web;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.sleuth.Span;

/**
 * @author Marcin Grzejszczak
 */
class TraceServletOutputStream extends ServletOutputStream {

	private static final Log log = LogFactory.getLog(MethodHandles.lookup().lookupClass());

	private final ServletOutputStream delegate;
	private final Span span;

	TraceServletOutputStream(ServletOutputStream delegate, Span span) {
		this.delegate = delegate;
		this.span = span;
	}

	@Override public boolean isReady() {
		return this.delegate.isReady();
	}

	@Override public void setWriteListener(WriteListener listener) {
		this.delegate.setWriteListener(listener);
	}

	@Override public void write(int b) throws IOException {
		this.delegate.write(b);
	}

	@Override public void print(String s) throws IOException {
		this.delegate.print(s);
	}

	@Override public void print(boolean b) throws IOException {
		this.delegate.print(b);
	}

	@Override public void print(char c) throws IOException {
		this.delegate.print(c);
	}

	@Override public void print(int i) throws IOException {
		this.delegate.print(i);
	}

	@Override public void print(long l) throws IOException {
		this.delegate.print(l);
	}

	@Override public void print(float f) throws IOException {
		this.delegate.print(f);
	}

	@Override public void print(double d) throws IOException {
		this.delegate.print(d);
	}

	@Override public void println() throws IOException {
		this.delegate.println();
	}

	@Override public void println(String s) throws IOException {
		this.delegate.println(s);
	}

	@Override public void println(boolean b) throws IOException {
		this.delegate.println(b);
	}

	@Override public void println(char c) throws IOException {
		this.delegate.println(c);
	}

	@Override public void println(int i) throws IOException {
		this.delegate.println(i);
	}

	@Override public void println(long l) throws IOException {
		this.delegate.println(l);
	}

	@Override public void println(float f) throws IOException {
		this.delegate.println(f);
	}

	@Override public void println(double d) throws IOException {
		this.delegate.println(d);
	}

	@Override public void write(byte[] b) throws IOException {
		this.delegate.write(b);
	}

	@Override public void write(byte[] b, int off, int len) throws IOException {
		this.delegate.write(b, off, len);
	}

	@Override public void flush() throws IOException {
		if (log.isTraceEnabled()) {
			log.trace("Will annotate SS once the stream is flushed");
		}
		SsLogSetter.annotateWithServerSendIfLogIsNotAlreadyPresent(this.span);
		this.delegate.flush();
	}

	@Override public void close() throws IOException {
		if (log.isTraceEnabled()) {
			log.trace("Will annotate SS once the stream is closed");
		}
		SsLogSetter.annotateWithServerSendIfLogIsNotAlreadyPresent(this.span);
		this.delegate.close();
	}
}
