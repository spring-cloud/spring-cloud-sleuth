/*
 * TODO License
 */

package org.springframework.cloud.sleuth.instrument.db;

import com.mongodb.DB;
import com.mongodb.ServerAddress;

import org.apache.commons.logging.Log;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.cloud.sleuth.ErrorParser;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanNamer;
import org.springframework.cloud.sleuth.TraceKeys;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * TODO Docs
 */
@Aspect
public class TraceDbAspect implements BeanFactoryAware {

	private static final Log log = org.apache.commons.logging.LogFactory
			.getLog(TraceDbAspect.class);

	private final Tracer tracer;
	private final SpanNamer spanNamer;
	private final TraceKeys traceKeys;
	private final ErrorParser errorParser;

	private BeanFactory beanFactory;

	public TraceDbAspect(Tracer tracer, TraceKeys traceKeys, SpanNamer spanNamer,
			ErrorParser errorParser) {
		this.tracer = tracer;
		this.spanNamer = spanNamer;
		this.traceKeys = traceKeys;
		this.errorParser = errorParser;
	}

	@Override
	public void setBeanFactory(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}

	@Pointcut("execution(public * org.springframework.data.repository.Repository+.*(..))")
	private void anyPublicRepositoryMethod() { }// NOSONAR

	@Around("anyPublicRepositoryMethod()")
	public Object traceDatabaseCall(ProceedingJoinPoint pjp) throws Throwable {
		log.warn("SOMETHING IS HAPPENING HERE");
		Span span = null;
		Object result = null;
		MongoTemplate template = this.beanFactory.getBean(MongoTemplate.class);
		DB db = template.getDb();
		ServerAddress addr = db.getMongo().getAllAddress().get(0);

		try {
			span = this.tracer.createSpan(pjp.getSignature().toShortString());
			this.tracer.addTag("span.kind", "client");
			this.tracer.addTag("db.type", "mongodb"); // TODO Generalize
			this.tracer.addTag("db.instance", db.getName());
			this.tracer.addTag("peer.address", addr.toString());
			span.logEvent(Span.CLIENT_SEND);
			result = pjp.proceed();
			// FIXME Calculate result size
			this.tracer.addTag("db.query.result.size",
				Integer.toString(result.toString().getBytes().length));
		} finally {
			this.tracer.close(span);
		}

		return result;
	}
}
