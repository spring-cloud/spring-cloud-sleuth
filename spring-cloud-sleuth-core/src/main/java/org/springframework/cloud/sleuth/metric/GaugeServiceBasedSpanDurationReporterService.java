/*
 * Copyright 2013-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.springframework.cloud.sleuth.metric;

import org.springframework.boot.actuate.metrics.GaugeService;
import org.springframework.util.StringUtils;

/**
 * Service to operate on span duration statistics.
 *
 * @author Marcin Grzejszczak
 */
public class GaugeServiceBasedSpanDurationReporterService implements SpanDurationReporterService {
	private final String metricPrefix;
	private final GaugeService gaugeService;

	public GaugeServiceBasedSpanDurationReporterService(String metricPrefix,
			GaugeService gaugeService) {
		this.metricPrefix = metricPrefix;
		this.gaugeService = gaugeService;
	}

	@Override
	public void submitDuration(String metricName, double duration) {
		String metricFullName = metricName;
		if (StringUtils.hasText(this.metricPrefix)) {
			metricFullName = this.metricPrefix + "." + metricName;
		}
		this.gaugeService.submit(metricFullName, duration);
	}
}