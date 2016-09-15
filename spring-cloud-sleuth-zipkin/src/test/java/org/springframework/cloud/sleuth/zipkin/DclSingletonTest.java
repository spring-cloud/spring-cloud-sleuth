package org.springframework.cloud.sleuth.zipkin;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.cloud.client.ServiceInstance;
import zipkin.Endpoint;

import static org.assertj.core.api.BDDAssertions.then;
import static org.junit.Assert.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * @author Matcin Wielgus
 */
@RunWith(MockitoJUnitRunner.class)
public class DclSingletonTest {

    @Mock
    DclSingleton.InstanceFactory<String> factory;

    @Test
    public void should_create_new_instance_of_object_on_first_call()
            throws Exception {
        DclSingleton<String> singleton = new DclSingleton<>();
        given(factory.newInstance()).willReturn("One");

        String value = singleton.get(factory);

        then(value).isEqualTo("One");
    }

    @Test
    public void should_no_new_instance_of_object_on_subsequent_calls()
            throws Exception {
        DclSingleton<String> singleton = new DclSingleton<>();
        given(factory.newInstance()).willReturn("One");

        String value1 = singleton.get(factory);
        String value2 = singleton.get(factory);

        then(value1).isEqualTo("One");
        then(value2).isEqualTo("One");
        verify(factory, times(1)).newInstance();
    }

    @Test
    public void should_create_new_instance_of_object_after_invalidation()
            throws Exception {
        DclSingleton<String> singleton = new DclSingleton<>();
        given(factory.newInstance()).willReturn("One");

        String value1 = singleton.get(factory);
        singleton.invalidate();

        given(factory.newInstance()).willReturn("Two");

        String value2 = singleton.get(factory);

        then(value1).isEqualTo("One");
        then(value2).isEqualTo("Two");
        verify(factory, times(2)).newInstance();
    }

}