package org.springframework.cloud.sleuth.instrument.web;

import java.util.regex.Pattern;

/**
 * Internal interface to describe patterns to skip tracing
 *
 * @author Marcin Grzejszczak
 * @since 2.0.0
 */
interface SkipPatternProvider {
	Pattern skipPattern();
}
