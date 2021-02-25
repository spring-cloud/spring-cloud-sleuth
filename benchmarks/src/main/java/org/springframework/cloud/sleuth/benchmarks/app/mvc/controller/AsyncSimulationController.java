package org.springframework.cloud.sleuth.benchmarks.app.mvc.controller;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.annotation.PreDestroy;

import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author Marcin Grzejszczak
 */
@RestController
public class AsyncSimulationController {
	private final ExecutorService pool = Executors.newWorkStealingPool();

	@RequestMapping("/foo")
	public String foo() {
		return "foo";
	}

	@RequestMapping("/bar")
	public Callable<String> bar() {
		return () -> "bar";
	}

	@RequestMapping("/async")
	public String asyncHttp() throws ExecutionException, InterruptedException {
		return this.async().get();
	}

	@Async
	public Future<String> async() {
		return this.pool.submit(() -> "async");
	}

	@PreDestroy
	public void clean() {
		this.pool.shutdownNow();
	}

	public ExecutorService getPool() {
		return this.pool;
	}
}
