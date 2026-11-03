package com.example.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

/**
 * Executor for calling several upstream APIs in parallel within one request (see
 * RouteBoardService).
 *
 * <ul>
 *   <li>virtual threads: waiting on HTTP costs almost nothing, so no pool sizing is needed
 *   <li>a concurrency limit: virtual threads make it easy to overwhelm a dependency; this bounds
 *       our parallel calls to MBTA, whose public limits are low
 *   <li>{@link MdcTaskDecorator}: the caller's requestId follows the work onto the virtual threads,
 *       so the parallel calls appear in the logs under the right request
 * </ul>
 */
@Configuration
public class UpstreamExecutorConfiguration {

  public static final String UPSTREAM_EXECUTOR = "upstreamCallExecutor";

  @Bean(name = UPSTREAM_EXECUTOR)
  public AsyncTaskExecutor upstreamCallExecutor() {
    SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("upstream-");
    executor.setVirtualThreads(true);
    executor.setConcurrencyLimit(50);
    executor.setTaskDecorator(new MdcTaskDecorator());
    return executor;
  }
}
