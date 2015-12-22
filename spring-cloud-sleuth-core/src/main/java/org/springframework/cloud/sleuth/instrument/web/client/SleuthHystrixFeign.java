package org.springframework.cloud.sleuth.instrument.web.client;

import org.springframework.cloud.sleuth.TraceManager;

import feign.Contract;
import feign.Feign;

/**
 * Sleuth implementation that wraps Hystrix execution in TraceCommand
 */
public final class SleuthHystrixFeign {

  public static Builder builder(TraceManager traceManager) {
    return new Builder(traceManager);
  }

  public static final class Builder extends Feign.Builder {

    public Builder(TraceManager traceManager) {
      invocationHandlerFactory(new SleuthHystrixInvocationHandler.Factory(traceManager));
      contract(new TraceCommandDelegatingContract(new Contract.Default()));
    }

    @Override
    public Feign.Builder contract(Contract contract) {
      return super.contract(new TraceCommandDelegatingContract(contract));
    }
  }
}