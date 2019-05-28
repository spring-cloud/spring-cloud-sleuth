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

package org.springframework.cloud.sleuth.zipkin2.sender;

import org.springframework.boot.autoconfigure.condition.AllNestedConditions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.NoneNestedConditions;
import org.springframework.cloud.sleuth.zipkin2.ZipkinAutoConfiguration;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.ConfigurationCondition.ConfigurationPhase;
import org.springframework.security.oauth2.client.OAuth2RestTemplate;
import org.springframework.security.oauth2.client.resource.OAuth2ProtectedResourceDetails;

class RestSenderCondition {

	static class OAuth2RestSenderCondition extends AllNestedConditions {

		OAuth2RestSenderCondition() {
			super(ConfigurationPhase.REGISTER_BEAN);
		}

		@ConditionalOnClass({ OAuth2ProtectedResourceDetails.class,
			OAuth2RestTemplate.class })
		static class Oauth2ClassesAvailables {

		}

		@ConditionalOnBean(value = OAuth2ProtectedResourceDetails.class, name = ZipkinAutoConfiguration.OAUTH2_RESOURCE_BEAN_NAME)
		static class OAuth2ResourceConfigured {

		}

	}

	static class StandardRestSenderCondition extends NoneNestedConditions {

		StandardRestSenderCondition() {
			super(ConfigurationPhase.REGISTER_BEAN);
		}

		@Conditional(OAuth2RestSenderCondition.class)
		static class OAuth2SenderActivated {

		}

	}

}
