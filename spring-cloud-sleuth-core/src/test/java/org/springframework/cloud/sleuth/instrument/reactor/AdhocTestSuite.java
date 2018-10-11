package org.springframework.cloud.sleuth.instrument.reactor;

import org.junit.Ignore;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

import org.springframework.cloud.sleuth.annotation.SleuthSpanCreatorAspectWebFluxTests;
import org.springframework.cloud.sleuth.instrument.web.TraceWebFluxTests;

@RunWith(Suite.class)
@Suite.SuiteClasses({ //
		TraceWebFluxTests.class, SleuthSpanCreatorAspectWebFluxTests.class //
})
@Ignore
public class AdhocTestSuite {

}