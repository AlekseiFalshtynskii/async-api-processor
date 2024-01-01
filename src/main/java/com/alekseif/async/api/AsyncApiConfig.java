package com.alekseif.async.api;

import static java.util.Optional.ofNullable;
import static org.slf4j.MDC.clear;
import static org.slf4j.MDC.getCopyOfContextMap;
import static org.slf4j.MDC.setContextMap;
import static org.springframework.web.context.request.RequestContextHolder.currentRequestAttributes;
import static org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes;
import static org.springframework.web.context.request.RequestContextHolder.setRequestAttributes;

import java.util.concurrent.Executor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.task.DelegatingSecurityContextAsyncTaskExecutor;

@Configuration
@ComponentScan(basePackageClasses = {
    AsyncRequestFileService.class,
    AsyncRequestExceptionHandler.class,
    AsyncRequestUserService.class,
    AsyncRequestController.class,
    AsyncRequestService.class,
})
@EntityScan(basePackageClasses = {
    AsyncRequestEntity.class,
})
@EnableJpaRepositories(basePackageClasses = {
    AsyncRequestRepository.class,
})
@EnableAsync
@RequiredArgsConstructor
@Slf4j
public class AsyncApiConfig {

  private final AsyncProperties asyncProperties;

  @Bean
  public Executor executor() {
    var taskExecutor = new ThreadPoolTaskExecutor();
    ofNullable(asyncProperties.getThreadNamePrefix()).ifPresent(taskExecutor::setThreadNamePrefix);
    ofNullable(asyncProperties.getAllowCoreThreadTimeOut()).ifPresent(taskExecutor::setAllowCoreThreadTimeOut);
    ofNullable(asyncProperties.getKeepAliveSeconds()).ifPresent(taskExecutor::setKeepAliveSeconds);
    ofNullable(asyncProperties.getCorePoolSize()).ifPresent(taskExecutor::setCorePoolSize);
    ofNullable(asyncProperties.getMaxPoolSize()).ifPresent(taskExecutor::setMaxPoolSize);
    ofNullable(asyncProperties.getQueueCapacity()).ifPresent(taskExecutor::setQueueCapacity);
    taskExecutor.setTaskDecorator(getTaskDecorator());
    taskExecutor.initialize();
    return new DelegatingSecurityContextAsyncTaskExecutor(taskExecutor);
  }

  public TaskDecorator getTaskDecorator() {
    return runnable -> {
      var requestAttributes = currentRequestAttributes();
      var contextMap = getCopyOfContextMap();
      return () -> {
        try {
          setRequestAttributes(requestAttributes);
          setContextMap(contextMap);
          runnable.run();
        } finally {
          resetRequestAttributes();
          clear();
        }
      };
    };
  }
}
