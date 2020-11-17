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

package org.springframework.cloud.sleuth.zipkin2;

import org.springframework.boot.autoconfigure.condition.ConditionMessage;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.ClassMetadata;
import org.springframework.util.StringUtils;

import static org.springframework.cloud.sleuth.zipkin2.ZipkinSenderConfigurationImportSelector.getType;

/** Attach this to any new sender configuration. */
class ZipkinSenderCondition extends SpringBootCondition {

	@Override
	public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata md) {
		String sourceClass = "";
		if (md instanceof ClassMetadata) {
			sourceClass = ((ClassMetadata) md).getClassName();
		}
		ConditionMessage.Builder message = ConditionMessage.forCondition("ZipkinSender", sourceClass);
		String property = context.getEnvironment().getProperty("spring.zipkin.sender.type");
		if (StringUtils.isEmpty(property)) {
			return ConditionOutcome.match(message.because("automatic sender type"));
		}
		String senderType = getType(((AnnotationMetadata) md).getClassName());
		if (property.equalsIgnoreCase(senderType)) {
			return ConditionOutcome.match(message.because(property + " sender type"));
		}
		return ConditionOutcome.noMatch(message.because(property + " sender type"));
	}

}
