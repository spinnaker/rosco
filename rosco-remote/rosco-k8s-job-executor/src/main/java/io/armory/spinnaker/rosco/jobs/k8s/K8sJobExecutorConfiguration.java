package io.armory.spinnaker.rosco.jobs.k8s;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.BatchV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.util.Config;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Slf4j
@Configuration
@ConditionalOnProperty("rosco.jobs.k8s.enabled")
@Import({K8sRunJobExecutor.class})
public class K8sJobExecutorConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public ApiClient apiClient() throws IOException {
    return Config.fromCluster();
  }

  @Bean
  public BatchV1Api batchV1Api(ApiClient apiClient) {
    return new BatchV1Api(apiClient);
  }

  @Bean
  public CoreV1Api coreV1Api(ApiClient apiClient) {
    return new CoreV1Api(apiClient);
  }
}
