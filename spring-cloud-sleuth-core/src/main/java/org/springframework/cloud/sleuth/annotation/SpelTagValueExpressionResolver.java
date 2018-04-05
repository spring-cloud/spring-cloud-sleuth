/*
 * Copyright 2013-2018 the original author or authors.
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

package org.springframework.cloud.sleuth.annotation;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;

/**
 * Uses SPEL to evaluate the expression. If an exception is thrown will return
 * the {@code toString()} of the parameter.
 *
 * @author Marcin Grzejszczak
 * @since 1.2.0
 */
class SpelTagValueExpressionResolver implements TagValueExpressionResolver {
	private static final Log log = LogFactory.getLog(SpelTagValueExpressionResolver.class);

	@Override
	public String resolve(String expression, Object parameter) {
		try {
			SimpleEvaluationContext context = SimpleEvaluationContext
					.forReadOnlyDataBinding()
					.build();
			ExpressionParser expressionParser = new SpelExpressionParser();
			Expression expressionToEvaluate = expressionParser.parseExpression(expression);
			return expressionToEvaluate.getValue(context, parameter, String.class);
		} catch (Exception e) {
			log.error("Exception occurred while tying to evaluate the SPEL expression [" + expression + "]", e);
		}
		return parameter.toString();
	}
}
