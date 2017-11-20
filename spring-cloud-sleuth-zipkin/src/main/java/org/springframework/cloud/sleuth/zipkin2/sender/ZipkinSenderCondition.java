package org.springframework.cloud.sleuth.zipkin2.sender;

import org.springframework.boot.autoconfigure.condition.ConditionMessage;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.boot.bind.RelaxedPropertyResolver;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.ClassMetadata;

import static org.springframework.cloud.sleuth.zipkin2.sender.ZipkinSenderConfigurationImportSelector.getType;

/** Attach this to any new sender configuration. */
class ZipkinSenderCondition extends SpringBootCondition {

	@Override
	public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata md) {
		String sourceClass = "";
		if (md instanceof ClassMetadata) {
			sourceClass = ((ClassMetadata) md).getClassName();
		}
		ConditionMessage.Builder message = ConditionMessage.forCondition("ZipkinSender", sourceClass);
		RelaxedPropertyResolver resolver = new RelaxedPropertyResolver(
				context.getEnvironment(), "spring.zipkin.sender.");
		if (!resolver.containsProperty("type")) {
			return ConditionOutcome.match(message.because("automatic sender type"));
		}

		String senderType = getType(((AnnotationMetadata) md).getClassName());
		String value = resolver.getProperty("type");
		if (value.equals(senderType)) {
			return ConditionOutcome.match(message.because(value + " sender type"));
		}
		return ConditionOutcome.noMatch(message.because(value + " sender type"));
	}
}
