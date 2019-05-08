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

package com.netflix.spinnaker.rosco.providers.tencent

import com.netflix.spinnaker.rosco.api.Bake
import com.netflix.spinnaker.rosco.api.BakeOptions
import com.netflix.spinnaker.rosco.api.BakeRequest
import com.netflix.spinnaker.rosco.providers.CloudProviderBakeHandler
import com.netflix.spinnaker.rosco.providers.tencent.config.RoscoTencentConfiguration
import com.netflix.spinnaker.rosco.providers.util.ImageNameFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
public class TencentBakeHandler extends CloudProviderBakeHandler {

  private static final String IMAGE_NAME_TOKEN = "ManagedImageName: "
  private static final String UNENCRYPTED_IMAGE_NAME_TOKEN = "ManagedImageId: "

  ImageNameFactory imageNameFactory = new ImageNameFactory()

  @Autowired
  RoscoTencentConfiguration.TencentBakeryDefaults tencentBakeryDefaults

  @Override
  def getBakeryDefaults() {
    return tencentBakeryDefaults
  }

  @Override
  BakeOptions getBakeOptions() {
    new BakeOptions(
      cloudProvider: BakeRequest.CloudProviderType.tencent,
      baseImages: tencentBakeryDefaults?.baseImages?.collect { it.baseImage }
    )
  }

  @Override
  String produceProviderSpecificBakeKeyComponent(String region, BakeRequest bakeRequest) {
//    if (!bakeRequest.vm_type) {
//      bakeRequest = bakeRequest.copyWith(vm_type: tencentBakeryDefaults.defaultVirtualizationType)
//    }
//
//    bakeRequest.with {
//      def enhancedNetworkingSegment = enhanced_networking ? 'enhancedNWEnabled' : 'enhancedNWDisabled'
//
//      return "$region:$vm_type:$enhancedNetworkingSegment"
//    }
    return null
  }

  @Override
  def findVirtualizationSettings(String region, BakeRequest bakeRequest) {
//    BakeRequest.VmType vmType = bakeRequest.vm_type ?: tencentBakeryDefaults.defaultVirtualizationType
//
//    def operatingSystemVirtualizationSettings = tencentBakeryDefaults?.baseImages.find {
//      it.baseImage.id == bakeRequest.base_os
//    }
//
//    if (!operatingSystemVirtualizationSettings) {
//      throw new IllegalArgumentException("No virtualization settings found for '$bakeRequest.base_os'.")
//    }
//
//    def virtualizationSettings = operatingSystemVirtualizationSettings?.virtualizationSettings.find {
//      it.region == region && it.virtualizationType == vmType
//    }
//
//    if (!virtualizationSettings) {
//      throw new IllegalArgumentException("No virtualization settings found for region '$region', operating system '$bakeRequest.base_os', and vm type '$vmType'.")
//    }
//
//    if (bakeRequest.base_ami) {
//      virtualizationSettings = virtualizationSettings.clone()
//      virtualizationSettings.sourceImageId = bakeRequest.base_ami
//    }
//
//    return virtualizationSettings
    return null
  }

  @Override
  Map buildParameterMap(String region, def virtualizationSettings, String imageName, BakeRequest bakeRequest, String appVersionStr) {
    def parameterMap = [
      tencent_region       : region,
//      tencent_instance_type: virtualizationSettings.instanceType,
//      tencent_source_image_id   : virtualizationSettings.sourceImageId,
      tencent_target_image   : imageName
    ]

//    if (virtualizationSettings.sshUserName) {
//      parameterMap.tencent_ssh_username = virtualizationSettings.sshUserName
//    }

    if (tencentBakeryDefaults.secretId && tencentBakeryDefaults.secretKey) {
      parameterMap.tencent_secret_id = tencentBakeryDefaults.secretId
      parameterMap.tencent_secret_key = tencentBakeryDefaults.secretKey
    }

    if (tencentBakeryDefaults.subnetId) {
      parameterMap.tencent_subnet_id = tencentBakeryDefaults.subnetId
    }

    if (tencentBakeryDefaults.vpcId) {
      parameterMap.tencent_vpc_id = tencentBakeryDefaults.vpcId
    }

    if (tencentBakeryDefaults.associatePublicIpAddress != null) {
      parameterMap.tencent_associate_public_ip_address = tencentBakeryDefaults.associatePublicIpAddress
    }

//    if (bakeRequest.enhanced_networking) {
//      parameterMap.tencent_ena_support = true
//    }
//
//    if (bakeRequest.build_info_url) {
//      parameterMap.build_info_url = bakeRequest.build_info_url
//    }

    if (appVersionStr) {
      parameterMap.appversion = appVersionStr
    }

    return parameterMap
  }

  @Override
  String getTemplateFileName(BakeOptions.BaseImage baseImage) {
    return baseImage.templateFile ?: tencentBakeryDefaults.templateFile
  }

  @Override
  Bake scrapeCompletedBakeResults(String region, String bakeId, String logsContent) {
    String amiId
    String imageName

    // TODO(duftler): Presently scraping the logs for the image name/id. Would be better to not be reliant on the log
    // format not changing. Resolve this by storing bake details in redis and querying oort for amiId from amiName.
    logsContent.eachLine { String line ->
      if (line =~ IMAGE_NAME_TOKEN) {
        imageName = line.split(" ").last()
      } else if (line =~ UNENCRYPTED_IMAGE_NAME_TOKEN) {
        line = line.replaceAll(UNENCRYPTED_IMAGE_NAME_TOKEN, "").trim()
        imageName = line.split(" ").first()
      } else if (line =~ "$region:") {
        amiId = line.split(" ").last()
      }
    }

    return new Bake(id: bakeId, ami: amiId, image_name: imageName)
  }
}
