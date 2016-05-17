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

  private static final String BUILDER_TYPE = "" //TODO
  private static final String IMAGE_NAME_TOKEN = "" //TODO

  @Autowired
  RoscoOpenstackConfiguration.OpenstackBakeryDefaults openstackBakeryDefaults

  @Override
  def getBakeryDefaults() {
    return openstackBakeryDefaults
  }

  @Override
  BakeOptions getBakeOptions() {
    //TODO
    new BakeOptions()
  }

  @Override
  String produceProviderSpecificBakeKeyComponent(String region, BakeRequest bakeRequest) {
    //TODO
    String key = ''
    key
  }

  @Override
  def findVirtualizationSettings(String region, BakeRequest bakeRequest) {
    //TODO
  }

  @Override
  Map buildParameterMap(String region, def openstackVirtualizationSettings, String imageName, BakeRequest bakeRequest) {
    //TODO
    def parameterMap = [:]
    parameterMap
  }

  @Override
  String getTemplateFileName() {
    //TODO
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
      //TODO
    }

    new Bake(id: bakeId, ami: amiId, image_name: imageName)
  }
}
