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

import java.io.PrintWriter;
import java.lang.invoke.MethodHandles;
import java.util.Locale;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.sleuth.Span;

/**
 * @author Marcin Grzejszczak
 */
class TracePrintWriter extends PrintWriter {

	private static final Log log = LogFactory.getLog(MethodHandles.lookup().lookupClass());

	private final PrintWriter delegate;
	private final Span span;

	TracePrintWriter(PrintWriter delegate, Span span) {
		super(delegate);
		this.delegate = delegate;
		this.span = span;
	}

	@Override public void flush() {
		if (log.isTraceEnabled()) {
			log.trace("Will annotate SS once the response is flushed");
		}
		SsLogSetter.annotateWithServerSendIfLogIsNotAlreadyPresent(this.span);
		this.delegate.flush();
	}

	@Override public void close() {
		if (log.isTraceEnabled()) {
			log.trace("Will annotate SS once the stream is closed");
		}
		SsLogSetter.annotateWithServerSendIfLogIsNotAlreadyPresent(this.span);
		this.delegate.close();
	}

	@Override public boolean checkError() {
		return this.delegate.checkError();
	}

	@Override public void write(int c) {
		this.delegate.write(c);
	}

	@Override public void write(char[] buf, int off, int len) {
		this.delegate.write(buf, off, len);
	}

	@Override public void write(char[] buf) {
		this.delegate.write(buf);
	}

	@Override public void write(String s, int off, int len) {
		this.delegate.write(s, off, len);
	}

	@Override public void write(String s) {
		this.delegate.write(s);
	}

	@Override public void print(boolean b) {
		this.delegate.print(b);
	}

	@Override public void print(char c) {
		this.delegate.print(c);
	}

	@Override public void print(int i) {
		this.delegate.print(i);
	}

	@Override public void print(long l) {
		this.delegate.print(l);
	}

	@Override public void print(float f) {
		this.delegate.print(f);
	}

	@Override public void print(double d) {
		this.delegate.print(d);
	}

	@Override public void print(char[] s) {
		this.delegate.print(s);
	}

	@Override public void print(String s) {
		this.delegate.print(s);
	}

	@Override public void print(Object obj) {
		this.delegate.print(obj);
	}

	@Override public void println() {
		this.delegate.println();
	}

	@Override public void println(boolean x) {
		this.delegate.println(x);
	}

	@Override public void println(char x) {
		this.delegate.println(x);
	}

	@Override public void println(int x) {
		this.delegate.println(x);
	}

	@Override public void println(long x) {
		this.delegate.println(x);
	}

	@Override public void println(float x) {
		this.delegate.println(x);
	}

	@Override public void println(double x) {
		this.delegate.println(x);
	}

	@Override public void println(char[] x) {
		this.delegate.println(x);
	}

	@Override public void println(String x) {
		this.delegate.println(x);
	}

	@Override public void println(Object x) {
		this.delegate.println(x);
	}

	@Override public PrintWriter printf(String format, Object... args) {
		return this.delegate.printf(format, args);
	}

	@Override public PrintWriter printf(Locale l, String format, Object... args) {
		return this.delegate.printf(l, format, args);
	}

	@Override public PrintWriter format(String format, Object... args) {
		return this.delegate.format(format, args);
	}

	@Override public PrintWriter format(Locale l, String format, Object... args) {
		return this.delegate.format(l, format, args);
	}

	@Override public PrintWriter append(CharSequence csq) {
		return this.delegate.append(csq);
	}

	@Override public PrintWriter append(CharSequence csq, int start, int end) {
		return this.delegate.append(csq, start, end);
	}

	@Override public PrintWriter append(char c) {
		return this.delegate.append(c);
	}
}
