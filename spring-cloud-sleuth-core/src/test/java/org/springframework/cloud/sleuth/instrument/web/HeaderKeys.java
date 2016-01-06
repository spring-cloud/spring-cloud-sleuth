package org.springframework.cloud.sleuth.instrument.web;

//Annotation key holding class for request and response headers
abstract class HeaderKeys {
	public static final String HTTP = "/http/";
	public static final String HTTP_URL = "/http/request/url";
	public static final String HTTP_URI = "/http/request/endpoint";
	public static final String HTTP_STATUS_CODE = "/http/response/status_code";
}
