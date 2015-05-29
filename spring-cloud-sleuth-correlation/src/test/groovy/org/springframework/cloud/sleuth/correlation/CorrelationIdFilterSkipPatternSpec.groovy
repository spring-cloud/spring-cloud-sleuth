package org.springframework.cloud.sleuth.correlation

import spock.lang.Specification
import spock.lang.Unroll

import javax.servlet.FilterChain
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@Unroll
class CorrelationIdFilterSkipPatternSpec extends Specification {

    CorrelationIdFilter filter = new CorrelationIdFilter(Stub(UuidGenerator), CorrelationIdFilter.DEFAULT_SKIP_PATTERN)

    def 'should skip meaningless URIs like #uri'() {
        given:
            HttpServletResponse responseMock = Mock(HttpServletResponse)
            HttpServletRequest requestMock = Mock(HttpServletRequest)
        and:
            requestMock.getRequestURI() >> uri
        when:
            filter.doFilter(requestMock, responseMock, Stub(FilterChain))
        then:
            0 * responseMock.addHeader(CorrelationIdHolder.CORRELATION_ID_HEADER, _ as String)
        where:
            uri                 | _
            '/api-docs'         | _
            '/api-docs/default' | _
            '/swagger'          | _
            '/trace'            | _
            '/metrics'          | _
            '/metrics/foo'      | _
            '/mappings'         | _
            '/autoconfig'       | _
            '/configprops'      | _
            '/info'             | _
            '/dump'             | _
            '/swagger/foo'      | _
            '/foo.js'           | _
            '/foo/bar.png'      | _
            '/foo/bar.html'     | _
            '/foo/bar.css'      | _
            '/foo/bar.js'       | _
            '/foo/bar.js'       | _
    }

    def 'should not skip #uri'() {
        given:
            HttpServletResponse responseMock = Mock(HttpServletResponse)
            HttpServletRequest requestMock = Mock(HttpServletRequest)
        and:
            requestMock.getRequestURI() >> uri
        when:
            filter.doFilter(requestMock, responseMock, Stub(FilterChain))
        then:
            1 * responseMock.addHeader(CorrelationIdHolder.CORRELATION_ID_HEADER, _ as String)
        where:
            uri                     | _
            '/business/api-docs'    | _
            '/business/swagger/foo' | _
            '/foo.js/service'       | _
    }


}
