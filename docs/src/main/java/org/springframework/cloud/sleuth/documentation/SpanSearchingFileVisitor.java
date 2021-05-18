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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.Expression;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.MethodDeclaration;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.MethodInvocation;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.ReturnStatement;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.StringLiteral;
import org.jboss.forge.roaster.model.JavaType;
import org.jboss.forge.roaster.model.JavaUnit;
import org.jboss.forge.roaster.model.impl.JavaEnumImpl;
import org.jboss.forge.roaster.model.source.EnumConstantSource;
import org.jboss.forge.roaster.model.source.JavaSource;
import org.jboss.forge.roaster.model.source.MemberSource;

import org.springframework.cloud.sleuth.docs.DocumentedSpan;
import org.springframework.cloud.sleuth.docs.EventValue;
import org.springframework.cloud.sleuth.docs.TagKey;

class SpanSearchingFileVisitor extends SimpleFileVisitor<Path> {

	private final Pattern pattern;

	private final Collection<SpanEntry> spanEntries;

	SpanSearchingFileVisitor(Pattern pattern, Collection<SpanEntry> spanEntries) {
		this.pattern = pattern;
		this.spanEntries = spanEntries;
	}

	@Override
	public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
		if (!pattern.matcher(file.toString()).matches()) {
			return FileVisitResult.CONTINUE;
		}
		else if (!file.toString().endsWith(".java")) {
			return FileVisitResult.CONTINUE;
		}
		try (InputStream stream = Files.newInputStream(file)) {
			JavaUnit unit = Roaster.parseUnit(stream);
			JavaType myClass = unit.getGoverningType();
			if (!(myClass instanceof JavaEnumImpl)) {
				return FileVisitResult.CONTINUE;
			}
			JavaEnumImpl myEnum = (JavaEnumImpl) myClass;
			if (!myEnum.getInterfaces().contains(DocumentedSpan.class.getCanonicalName())) {
				return FileVisitResult.CONTINUE;
			}
			System.out.println("Checking [" + myEnum.getName() + "]");
			if (myEnum.getEnumConstants().size() == 0) {
				return FileVisitResult.CONTINUE;
			}
			for (EnumConstantSource enumConstant : myEnum.getEnumConstants()) {
				SpanEntry entry = parseSpan(enumConstant, myEnum);
				if (entry != null) {
					spanEntries.add(entry);
					System.out.println(
							"Found [" + entry.tagKeys.size() + "] tags and [" + entry.events.size() + "] events");
				}
			}
			return FileVisitResult.CONTINUE;
		}
	}

	private SpanEntry parseSpan(EnumConstantSource enumConstant, JavaEnumImpl myEnum) {
		List<MemberSource<EnumConstantSource.Body, ?>> members = enumConstant.getBody().getMembers();
		if (members.isEmpty()) {
			return null;
		}
		String name = "";
		String description = enumConstant.getJavaDoc().getText();
		Collection<KeyValueEntry> tags = new TreeSet<>();
		Collection<KeyValueEntry> events = new TreeSet<>();
		for (MemberSource<EnumConstantSource.Body, ?> member : members) {
			Object internal = member.getInternal();
			if (!(internal instanceof MethodDeclaration)) {
				return null;
			}
			MethodDeclaration methodDeclaration = (MethodDeclaration) internal;
			String methodName = methodDeclaration.getName().getIdentifier();
			if ("getName".equals(methodName)) {
				name = readStringReturnValue(methodDeclaration);
			}
			else if ("getTagKeys".equals(methodName)) {
				tags.addAll(keyValueEntries(myEnum, methodDeclaration, TagKey.class));
			}
			else if ("getEvents".equals(methodName)) {
				events.addAll(keyValueEntries(myEnum, methodDeclaration, EventValue.class));
			}
			else {
				return null;
			}
		}
		return new SpanEntry(name, myEnum.getCanonicalName(), enumConstant.getName(), description, tags, events);
	}

	private Collection<KeyValueEntry> keyValueEntries(JavaEnumImpl myEnum, MethodDeclaration methodDeclaration,
			Class requiredClass) {
		Collection<String> enumNames = readClassValue(methodDeclaration);
		Collection<KeyValueEntry> keyValues = new TreeSet<>();
		enumNames.forEach(enumName -> {
			List<JavaSource<?>> nestedTypes = myEnum.getNestedTypes();
			JavaSource<?> nestedSource = nestedTypes.stream()
					.filter(javaSource -> javaSource.getName().equals(enumName)).findFirst().orElseThrow(
							() -> new IllegalStateException("There's no nested type with name [" + enumName + "]"));
			updateKeyValuesFromEnum(myEnum, nestedSource, requiredClass, keyValues);
		});
		return keyValues;
	}

	private void updateKeyValuesFromEnum(JavaEnumImpl parentEnum, JavaSource<?> source, Class requiredClass,
			Collection<KeyValueEntry> keyValues) {
		if (!(source instanceof JavaEnumImpl)) {
			return;
		}
		JavaEnumImpl myEnum = (JavaEnumImpl) source;
		if (!myEnum.getInterfaces().contains(requiredClass.getCanonicalName())) {
			return;
		}
		System.out.println("Checking [" + parentEnum.getName() + "." + myEnum.getName() + "]");
		if (myEnum.getEnumConstants().size() == 0) {
			return;
		}
		for (EnumConstantSource enumConstant : myEnum.getEnumConstants()) {
			String keyValue = enumKeyValue(enumConstant);
			keyValues.add(new KeyValueEntry(keyValue, enumConstant.getJavaDoc().getText()));
		}
	}

	private String enumKeyValue(EnumConstantSource enumConstant) {
		List<MemberSource<EnumConstantSource.Body, ?>> members = enumConstant.getBody().getMembers();
		if (members.isEmpty()) {
			System.err.println("No method declarations in the enum.");
			return "";
		}
		Object internal = members.get(0).getInternal();
		if (!(internal instanceof MethodDeclaration)) {
			System.err.println("Can't read the member [" + internal.getClass() + "] as a method declaration.");
			return "";
		}
		MethodDeclaration methodDeclaration = (MethodDeclaration) internal;
		if (methodDeclaration.getBody().statements().isEmpty()) {
			System.err.println("Body was empty. Continuing...");
			return "";
		}
		return stringFromReturnMethodDeclaration(methodDeclaration);
	}

	private String stringFromReturnMethodDeclaration(MethodDeclaration methodDeclaration) {
		Object statement = methodDeclaration.getBody().statements().get(0);
		if (!(statement instanceof ReturnStatement)) {
			System.err.println("Statement [" + statement.getClass() + "] is not a return statement.");
			return "";
		}
		ReturnStatement returnStatement = (ReturnStatement) statement;
		Expression expression = returnStatement.getExpression();
		if (!(expression instanceof StringLiteral)) {
			System.err.println("Statement [" + statement.getClass() + "] is not a string literal statement.");
			return "";
		}
		return ((StringLiteral) expression).getLiteralValue();
	}

	private String readStringReturnValue(MethodDeclaration methodDeclaration) {
		return stringFromReturnMethodDeclaration(methodDeclaration);
	}

	private Collection<String> readClassValue(MethodDeclaration methodDeclaration) {
		Object statement = methodDeclaration.getBody().statements().get(0);
		if (!(statement instanceof ReturnStatement)) {
			System.err.println("Statement [" + statement.getClass() + "] is not a return statement.");
			return Collections.emptyList();
		}
		ReturnStatement returnStatement = (ReturnStatement) statement;
		Expression expression = returnStatement.getExpression();
		if (!(expression instanceof MethodInvocation)) {
			System.err.println("Statement [" + statement.getClass() + "] is not a method invocation.");
			return Collections.emptyList();
		}
		MethodInvocation methodInvocation = (MethodInvocation) expression;
		if ("merge".equals(methodInvocation.getName().getIdentifier())) {
			// TODO: There must be a better way to do this...
			// TagKey.merge(TestSpanTags.values(),AsyncSpanTags.values())
			String invocationString = methodInvocation.toString();
			Matcher matcher = Pattern.compile("([a-zA-Z]+.values)").matcher(invocationString);
			Collection<String> classNames = new TreeSet<>();
			while (matcher.find()) {
				String className = matcher.group(1).split("\\.")[0];
				classNames.add(className);
			}
			return classNames;
		}
		else if (!methodInvocation.toString().endsWith(".values()")) {
			throw new IllegalStateException("You have to use the static .values() method on the enum that implements "
					+ TagKey.class + " or " + EventValue.class
					+ " interface or use [TagKey.merge(...)] method to merge multiple values from tags");
		}
		// will return Tags
		return Collections.singletonList(methodInvocation.getExpression().toString());
	}

}
