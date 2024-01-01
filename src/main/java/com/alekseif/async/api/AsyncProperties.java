package com.alekseif.async.api;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties("async.properties")
@Data
public class AsyncProperties {

  private String threadNamePrefix;
  private Boolean allowCoreThreadTimeOut;
  private Integer keepAliveSeconds;
  private Integer corePoolSize;
  private Integer maxPoolSize;
  private Integer queueCapacity;
}
