/*
 * Copyright 2013-2021 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.deployer;

import org.springframework.cloud.sleuth.docs.DocumentedSpan;
import org.springframework.cloud.sleuth.docs.EventValue;
import org.springframework.cloud.sleuth.docs.TagKey;

enum SleuthDeployerSpan implements DocumentedSpan {

	/**
	 * Span created upon deploying of an application.
	 */
	DEPLOYER_DEPLOY_SPAN {
		@Override
		public String getName() {
			return "deploy";
		}

		@Override
		public TagKey[] getTagKeys() {
			return Tags.values();
		}

		@Override
		public EventValue[] getEvents() {
			return Events.values();
		}
	},

	/**
	 * Span created upon undeploying of an application.
	 */
	DEPLOYER_UNDEPLOY_SPAN {
		@Override
		public String getName() {
			return "undeploy";
		}

		@Override
		public TagKey[] getTagKeys() {
			return Tags.values();
		}

		@Override
		public EventValue[] getEvents() {
			return Events.values();
		}
	},

	/**
	 * Span created upon asking for a status of a deployed application.
	 */
	DEPLOYER_STATUS_SPAN {
		@Override
		public String getName() {
			return "status";
		}

		@Override
		public TagKey[] getTagKeys() {
			return Tags.values();
		}

		@Override
		public EventValue[] getEvents() {
			return Events.values();
		}
	},

	/**
	 * Span created upon asking for statuses of deployed applications.
	 */
	DEPLOYER_STATUSES_SPAN {
		@Override
		public String getName() {
			return "statuses";
		}

		@Override
		public TagKey[] getTagKeys() {
			return Tags.values();
		}

		@Override
		public EventValue[] getEvents() {
			return Events.values();
		}
	},

	/**
	 * Span created upon asking for logs of deployed applications.
	 */
	DEPLOYER_GET_LOG_SPAN {
		@Override
		public String getName() {
			return "getLog";
		}

		@Override
		public TagKey[] getTagKeys() {
			return Tags.values();
		}

		@Override
		public EventValue[] getEvents() {
			return Events.values();
		}
	},

	/**
	 * Span created upon asking for logs of deployed applications.
	 */
	DEPLOYER_SCALE_SPAN {
		@Override
		public String getName() {
			return "scale";
		}

		@Override
		public TagKey[] getTagKeys() {
			return TagKey.merge(Tags.values(), ScaleTags.values());
		}

		@Override
		public EventValue[] getEvents() {
			return Events.values();
		}
	};

	enum Tags implements TagKey {

		/**
		 * Name of the platform to which apps are being deployed.
		 */
		PLATFORM_NAME {
			@Override
			public String getKey() {
				return "deployer.platform.name";
			}
		},

		/**
		 * ID of the deployed application.
		 */
		APP_ID {
			@Override
			public String getKey() {
				return "deployer.app.id";
			}
		},

		/**
		 * Name of the deployed application.
		 */
		APP_NAME {
			@Override
			public String getKey() {
				return "deployer.app.name";
			}
		},

		/**
		 * Group of the deployed application.
		 */
		APP_GROUP {
			@Override
			public String getKey() {
				return "deployer.app.group";
			}
		},

		/**
		 * CloudFoundry API URL.
		 */
		CF_URL {
			@Override
			public String getKey() {
				return "deployer.platform.cf.url";
			}
		},

		/**
		 * CloudFoundry org.
		 */
		CF_ORG {
			@Override
			public String getKey() {
				return "deployer.platform.cf.org";
			}
		},

		/**
		 * CloudFoundry space.
		 */
		CF_SPACE {
			@Override
			public String getKey() {
				return "deployer.platform.cf.space";
			}
		},

		/**
		 * Kubernetes API URL.
		 */
		K8S_URL {
			@Override
			public String getKey() {
				return "deployer.platform.k8s.url";
			}
		},

		/**
		 * Kubernetes namespace.
		 */
		K8S_NAMESPACE {
			@Override
			public String getKey() {
				return "deployer.platform.k8s.namespace";
			}
		},

	}

	enum ScaleTags implements TagKey {

		/**
		 * Scale command deployment id.
		 */
		DEPLOYER_SCALE_DEPLOYMENT_ID {
			@Override
			public String getKey() {
				return "deployer.scale.deploymentId";
			}
		},

		/**
		 * Scale count.
		 */
		DEPLOYER_SCALE_COUNT {
			@Override
			public String getKey() {
				return "deployer.scale.count";
			}
		}

	}

	enum Events implements EventValue {

		/**
		 * When deployer started deploying the application.
		 */
		DEPLOYER_START {
			@Override
			public String getValue() {
				return "deployer.start";
			}
		},

		/**
		 * When deployer changes the state of the deployed application.
		 */
		DEPLOYER_STATUS_CHANGE {
			@Override
			public String getValue() {
				return "%s";
			}
		}

	}

}
