/*
 * Copyright 2015 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.rosco.providers.tencent.config

import com.netflix.spinnaker.rosco.api.BakeOptions
import com.netflix.spinnaker.rosco.api.BakeRequest
import com.netflix.spinnaker.rosco.providers.registry.CloudProviderBakeHandlerRegistry
import com.netflix.spinnaker.rosco.providers.tencent.TencentBakeHandler
import groovy.transform.AutoClone
import groovy.transform.AutoCloneStyle
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration

import javax.annotation.PostConstruct

@Slf4j
@Configuration
@ConditionalOnProperty('tencent.enabled')
@ComponentScan('com.netflix.spinnaker.rosco.providers.tencent')
class RoscoTencentConfiguration {

  @Autowired
  CloudProviderBakeHandlerRegistry cloudProviderBakeHandlerRegistry

  @Autowired
  TencentBakeHandler tencentBakeHandler

  @Bean
  @ConfigurationProperties('tencent.bakeryDefaults')
  TencentBakeryDefaults tencentBakeryDefaults(@Value('${tencent.bakeryDefaults.defaultVirtualizationType:hvm}') BakeRequest.VmType defaultVirtualizationType) {
    new TencentBakeryDefaults(defaultVirtualizationType: defaultVirtualizationType)
  }

  static class TencentBakeryDefaults {
    String secretId
    String secretKey
    String subnetId
    String vpcId
    Boolean associatePublicIpAddress
    String templateFile
    BakeRequest.VmType defaultVirtualizationType
    List<TencentOperatingSystemVirtualizationSettings> baseImages = []
  }

  static class TencentOperatingSystemVirtualizationSettings {
    BakeOptions.BaseImage baseImage
    List<TencentVirtualizationSettings> virtualizationSettings = []
  }

  @AutoClone(style = AutoCloneStyle.SIMPLE)
  static class TencentVirtualizationSettings {
    String region
    String zone
//    BakeRequest.VmType virtualizationType
    String instanceType
    String sourceImageId
    String imageName
    String sshUserName
  }

  @PostConstruct
  void init() {
    cloudProviderBakeHandlerRegistry.register(BakeRequest.CloudProviderType.tencent, tencentBakeHandler)
  }
}
