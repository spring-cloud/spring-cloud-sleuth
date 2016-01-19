/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.cloud.sleuth.instrument.web.client;

import static feign.Util.checkNotNull;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Map;

import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.instrument.hystrix.TraceCommand;

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;

import feign.InvocationHandlerFactory;
import feign.InvocationHandlerFactory.MethodHandler;
import feign.Target;

/**
 * Wraps execution in Sleuth's TraceCommand
 */
final class SleuthHystrixInvocationHandler implements InvocationHandler {

  private final Target<?> target;
  private final Map<Method, MethodHandler> dispatch;
  private final Tracer tracer;

  SleuthHystrixInvocationHandler(Target<?> target, Map<Method, MethodHandler> dispatch, Tracer tracer) {
    this.tracer = checkNotNull(tracer, "traceManager");
    this.target = checkNotNull(target, "target");
    this.dispatch = checkNotNull(dispatch, "dispatch");
  }

  @Override
  public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable {
    String groupKey = this.target.name();
    String commandKey = method.getName();
    HystrixCommand.Setter setter = HystrixCommand.Setter
        .withGroupKey(HystrixCommandGroupKey.Factory.asKey(groupKey))
        .andCommandKey(HystrixCommandKey.Factory.asKey(commandKey));

    HystrixCommand<Object> hystrixCommand = new TraceCommand<Object>(this.tracer, setter) {
      @Override
      public Object doRun() throws Exception {
        try {
          return SleuthHystrixInvocationHandler.this.dispatch.get(method).invoke(args);
        } catch (Exception e) {
          throw e;
        } catch (Throwable t) {
          throw (Error)t;
        }
      }
    };

    if (HystrixCommand.class.isAssignableFrom(method.getReturnType())) {
      return hystrixCommand;
    }
    return hystrixCommand.execute();
  }

  static final class Factory implements InvocationHandlerFactory {

    private final Tracer tracer;

    public Factory(Tracer tracer) {
      this.tracer = tracer;
    }

    @Override
    public InvocationHandler create(@SuppressWarnings("rawtypes") Target target, Map<Method, MethodHandler> dispatch) {
      return new SleuthHystrixInvocationHandler(target, dispatch, this.tracer);
    }
  }
}