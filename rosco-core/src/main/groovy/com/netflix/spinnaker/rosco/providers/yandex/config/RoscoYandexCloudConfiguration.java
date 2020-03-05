/*
 * Copyright 2020 YANDEX LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.rosco.providers.yandex.config;

import com.netflix.spinnaker.rosco.api.BakeOptions;
import com.netflix.spinnaker.rosco.api.BakeRequest;
import com.netflix.spinnaker.rosco.providers.registry.CloudProviderBakeHandlerRegistry;
import com.netflix.spinnaker.rosco.providers.yandex.YandexBakeHandler;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.PostConstruct;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty("yandex.enabled")
@ComponentScan("com.netflix.spinnaker.rosco.providers.yandex")
public class RoscoYandexCloudConfiguration {
  private final CloudProviderBakeHandlerRegistry cloudProviderBakeHandlerRegistry;
  private final YandexBakeHandler yandexBakeHandler;

  @Autowired
  public RoscoYandexCloudConfiguration(
      CloudProviderBakeHandlerRegistry cloudProviderBakeHandlerRegistry,
      YandexBakeHandler yandexBakeHandler) {
    this.cloudProviderBakeHandlerRegistry = cloudProviderBakeHandlerRegistry;
    this.yandexBakeHandler = yandexBakeHandler;
  }

  @PostConstruct
  void init() {
    cloudProviderBakeHandlerRegistry.register(
        BakeRequest.CloudProviderType.yandex, yandexBakeHandler);
  }

  @Bean
  @ConfigurationProperties("yandex")
  public YandexConfigurationProperties yandexConfigurationProperties() {
    return new YandexConfigurationProperties();
  }

  @Bean
  @ConfigurationProperties("yandex.bakery-defaults")
  YandexBakeryDefaults yandexCloudBakeryDefaults() {
    return new YandexBakeryDefaults();
  }

  @Data
  public static class YandexBakeryDefaults {
    private String zone;

    private String templateFile;
    private List<YandexOperatingSystemVirtualizationSettings> baseImages = new ArrayList<>();
  }

  @Data
  public static class YandexOperatingSystemVirtualizationSettings {
    private BakeOptions.BaseImage baseImage;
    private YandexVirtualizationSettings virtualizationSettings;
  }

  @Data
  public static class YandexVirtualizationSettings {
    private String sourceImageId;
    private String sourceImageFamily;
    private String sourceImageName;
    private String sourceImageFolderId;
  }
}
