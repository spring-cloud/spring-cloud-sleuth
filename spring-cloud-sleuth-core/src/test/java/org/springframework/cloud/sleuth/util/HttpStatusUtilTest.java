package org.springframework.cloud.sleuth.util;

import org.junit.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author Florian Lautenschlager
 */
public class HttpStatusUtilTest {

	@Test
	public void isRegularStatus() throws Exception {
		for (HttpStatus status : HttpStatus.values()) {
			then(HttpStatusUtil.isRegularStatus(status.value())).isTrue();
		}
	}

	@Test
	public void isIrregularStatus() throws Exception {
		then(HttpStatusUtil.isRegularStatus(560)).isFalse();
	}
}