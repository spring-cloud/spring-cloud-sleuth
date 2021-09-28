package org.springframework.cloud.sleuth.instrument.web.client.feign;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.cloud.openfeign.FeignContext;
import org.springframework.context.ApplicationContext;


class FeignContextBeanPostProcessorTest {

    @Test
    void should_have_parent_after_post_processed() {
        FeignContextBeanPostProcessor feignContextBeanPostProcessor = new FeignContextBeanPostProcessor(Mockito.mock(BeanFactory.class));
        FeignContext feignContext = new FeignContext();
        feignContext.setApplicationContext(Mockito.mock(ApplicationContext.class));
        TraceFeignContext traceFeignContext = (TraceFeignContext) feignContextBeanPostProcessor.postProcessAfterInitialization(feignContext, "test");
        Assertions.assertNotNull(traceFeignContext.getParent());
    }
}