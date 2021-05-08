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

package org.springframework.cloud.sleuth;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.importer.Location;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packagesOf = ArchitectureTests.class, importOptions = ArchitectureTests.ProductionCode.class)
public class ArchitectureTests {

	// TODO: Add "..org.springframework.beans.factory.config.." - BeanPostProcessors
	// should end up in [autoconfig]
	@ArchTest
	public static final ArchRule should_not_contain_any_spring_configuration_reference_in_module = noClasses().should()
			.dependOnClassesThat().resideInAnyPackage("..org.springframework.boot.context.properties..",
					"..org.springframework.context.annotation..");

	static class ProductionCode implements ImportOption {

		@Override
		public boolean includes(Location location) {
			return Predefined.DO_NOT_INCLUDE_TESTS.includes(location)
					&& Predefined.DO_NOT_INCLUDE_JARS.includes(location);
		}

	}

}
