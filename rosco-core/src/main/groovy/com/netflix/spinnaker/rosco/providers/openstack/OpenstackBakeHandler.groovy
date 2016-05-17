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

package com.netflix.spinnaker.rosco.providers.openstack

import com.netflix.spinnaker.rosco.api.Bake
import com.netflix.spinnaker.rosco.api.BakeOptions
import com.netflix.spinnaker.rosco.api.BakeRequest
import com.netflix.spinnaker.rosco.providers.CloudProviderBakeHandler
import com.netflix.spinnaker.rosco.providers.openstack.config.RoscoOpenstackConfiguration
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
public class OpenstackBakeHandler extends CloudProviderBakeHandler {

  private static final String BUILDER_TYPE = "openstack-(chroot|ebs)"
  private static final String IMAGE_NAME_TOKEN = "openstack-(chroot|ebs): Creating the AMI:"

  @Autowired
  RoscoOpenstackConfiguration.OpenstackBakeryDefaults openstackBakeryDefaults

  @Override
  def getBakeryDefaults() {
    return openstackBakeryDefaults
  }

  @Override
  BakeOptions getBakeOptions() {
    new BakeOptions(
      cloudProvider: BakeRequest.CloudProviderType.openstack,
      baseImages: openstackBakeryDefaults?.baseImages?.collect { it.baseImage }
    )
  }

  @Override
  String produceProviderSpecificBakeKeyComponent(String region, BakeRequest bakeRequest) {
    if (!bakeRequest.vm_type) {
      bakeRequest = bakeRequest.copyWith(vm_type: openstackBakeryDefaults.defaultVirtualizationType)
    }

    bakeRequest.with {
      def enhancedNetworkingSegment = enhanced_networking ? 'enhancedNWEnabled' : 'enhancedNWDisabled'

      return "$region:$vm_type:$enhancedNetworkingSegment"
    }
  }

  @Override
  def findVirtualizationSettings(String region, BakeRequest bakeRequest) {
    BakeRequest.VmType vm_type = bakeRequest.vm_type ?: openstackBakeryDefaults.defaultVirtualizationType

    def openstackOperatingSystemVirtualizationSettings = openstackBakeryDefaults?.baseImages.find {
      it.baseImage.id == bakeRequest.base_os
    }

    if (!openstackOperatingSystemVirtualizationSettings) {
      throw new IllegalArgumentException("No virtualization settings found for '$bakeRequest.base_os'.")
    }

    def openstackVirtualizationSettings = openstackOperatingSystemVirtualizationSettings?.virtualizationSettings.find {
      it.region == region && it.virtualizationType == vm_type
    }

    if (!openstackVirtualizationSettings) {
      throw new IllegalArgumentException("No virtualization settings found for region '$region', operating system '$bakeRequest.base_os', and vm type '$vm_type'.")
    }

    if (bakeRequest.base_ami) {
      openstackVirtualizationSettings = openstackVirtualizationSettings.clone()
      openstackVirtualizationSettings.sourceAmi = bakeRequest.base_ami
    }

    return openstackVirtualizationSettings
  }

  @Override
  Map buildParameterMap(String region, def openstackVirtualizationSettings, String imageName, BakeRequest bakeRequest) {
    def parameterMap = [
      openstack_region       : region,
      openstack_ssh_username : openstackVirtualizationSettings.sshUserName,
      openstack_instance_type: openstackVirtualizationSettings.instanceType,
      openstack_source_ami   : openstackVirtualizationSettings.sourceAmi,
      openstack_target_ami   : imageName
    ]

    if (openstackBakeryDefaults.openstackAccessKey && openstackBakeryDefaults.openstackSecretKey) {
      parameterMap.openstack_access_key = openstackBakeryDefaults.openstackAccessKey
      parameterMap.openstack_secret_key = openstackBakeryDefaults.openstackSecretKey
    }

    if (openstackBakeryDefaults.openstackSubnetId) {
      parameterMap.openstack_subnet_id = openstackBakeryDefaults.openstackSubnetId
    }

    if (openstackBakeryDefaults.openstackVpcId) {
      parameterMap.openstack_vpc_id = openstackBakeryDefaults.openstackVpcId
    }

    if (openstackBakeryDefaults.openstackAssociatePublicIpAddress != null) {
      parameterMap.openstack_associate_public_ip_address = openstackBakeryDefaults.openstackAssociatePublicIpAddress
    }

    if (bakeRequest.enhanced_networking) {
      parameterMap.openstack_enhanced_networking = true
    }

    return parameterMap
  }

  @Override
  String getTemplateFileName() {
    return openstackBakeryDefaults.templateFile
  }

  @Override
  boolean isProducerOf(String logsContentFirstLine) {
    logsContentFirstLine =~ BUILDER_TYPE
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
      } else if (line =~ "$region:") {
        amiId = line.split(" ").last()
      }
    }

    return new Bake(id: bakeId, ami: amiId, image_name: imageName)
  }
}
