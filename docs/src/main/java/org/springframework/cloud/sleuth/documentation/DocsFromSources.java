/*
 * Copyright 2013-2020 the original author or authors.
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

package org.springframework.cloud.sleuth.documentation;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.TreeSet;
import java.util.regex.Pattern;

public class DocsFromSources {

	private final File projectRoot;

	private final String inclusionPattern;

	private final File outputDir;

	public DocsFromSources(File projectRoot, String inclusionPattern, File outputDir) {
		this.projectRoot = projectRoot;
		this.inclusionPattern = inclusionPattern;
		this.outputDir = outputDir;
	}

	public static void main(String... args) {
		String projectRoot = args[0];
		String inclusionPattern = args[1];
		inclusionPattern = inclusionPattern.replace("/", File.separator);
		String output = args[2];
		new DocsFromSources(new File(projectRoot), inclusionPattern, new File(output)).generate();
	}

	public void generate() {
		Path path = this.projectRoot.toPath();
		Pattern pattern = Pattern.compile(this.inclusionPattern);
		System.out.println("Inclusion pattern is [" + this.inclusionPattern + "]");
		Collection<SpanEntry> spanEntries = new TreeSet<>();
		FileVisitor<Path> fv = new SpanSearchingFileVisitor(pattern, spanEntries);
		try {
			Files.walkFileTree(path, fv);
			Path output = new File(this.outputDir, "_spans.adoc").toPath();
			StringBuilder stringBuilder = new StringBuilder();
			System.out.println("======================================");
			System.out.println("Summary of sources analysis");
			System.out.println("Found [" + spanEntries.size() + "] spans");
			System.out.println(
					"Found [" + spanEntries.stream().flatMap(e -> e.tagKeys.stream()).distinct().count() + "] tags");
			System.out.println(
					"Found [" + spanEntries.stream().flatMap(e -> e.events.stream()).distinct().count() + "] events");
			spanEntries.forEach(spanEntry -> stringBuilder.append(spanEntry.toString()).append("\n\n"));
			Files.write(output, stringBuilder.toString().getBytes());
		}
		catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

}
