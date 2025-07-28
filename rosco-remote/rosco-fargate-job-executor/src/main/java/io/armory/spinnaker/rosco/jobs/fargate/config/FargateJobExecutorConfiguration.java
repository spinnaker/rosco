package io.armory.spinnaker.rosco.jobs.fargate.config;

import com.amazonaws.auth.AWSCredentialsProviderChain;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.bettercloud.vault.Vault;
import com.bettercloud.vault.VaultConfig;
import com.bettercloud.vault.VaultException;
import com.netflix.spinnaker.rosco.providers.util.PackerCommandFactory;
import io.armory.spinnaker.rosco.jobs.fargate.FargateJobExecutor;
import io.armory.spinnaker.rosco.jobs.fargate.FargatePackerCommandFactory;
import io.armory.spinnaker.rosco.jobs.fargate.VaultIamAuthRequestFactory;
import io.armory.spinnaker.rosco.jobs.fargate.model.FargateConfig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

@Slf4j
@Configuration
@ConditionalOnProperty("rosco.jobs.fargate.enabled")
@Import({FargateJobExecutor.class, VaultIamAuthRequestFactory.class})
public class FargateJobExecutorConfiguration {

  @Bean
  @ConfigurationProperties(prefix = "rosco.jobs.fargate")
  FargateConfig fargateConfig() {
    return new FargateConfig();
  }

  @Bean
  AWSCredentialsProviderChain credentialsProviderChain() {
    return new DefaultAWSCredentialsProviderChain();
  }

  @Bean
  public Function<String, Vault> vaultClientFactory(FargateConfig fargateConfig) {
    var vaultConfig = fargateConfig.getVault();
    return (String token) -> {
      VaultConfig config;
      try {
        config =
            new VaultConfig()
                .address(vaultConfig.getAddress())
                .token(token)
                .openTimeout(vaultConfig.getOpenTimeoutSeconds())
                .readTimeout(vaultConfig.getReadTimeoutSeconds())
                .build();
      } catch (VaultException e) {
        throw new RuntimeException("Failed to bootstrap Vault config", e);
      }
      return new Vault(config);
    };
  }

  @Bean
  @Primary
  PackerCommandFactory remotePackerCommandFactory() {
    return new FargatePackerCommandFactory();
  }

  @Bean
  @Qualifier("configMap")
  public Map<String, Object> getConfigMap(@Value("${rosco.config-dir}") String configDir)
      throws IOException {
    Map<String, Object> configMap =
        Files.walk(Paths.get(configDir))
            .filter(file -> Files.isRegularFile(file) && Files.isReadable(file))
            .collect(
                Collectors.toMap(
                    file -> file.getFileName().toString(),
                    file -> {
                      try {
                        return Files.readString(file, StandardCharsets.UTF_8);
                      } catch (IOException e) {
                        throw new RuntimeException(
                            String.format(
                                "Failed to read configuration file: '%s' as UTF-8 text",
                                file.toString()),
                            e);
                      }
                    },
                    (originalFile, duplicateFile) -> originalFile));
    return configMap;
  }
}
