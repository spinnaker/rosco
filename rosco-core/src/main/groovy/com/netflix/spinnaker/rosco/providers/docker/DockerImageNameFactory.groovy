package com.netflix.spinnaker.rosco.providers.docker

import com.netflix.spinnaker.rosco.api.BakeRequest
import com.netflix.spinnaker.rosco.providers.util.ImageNameFactory
import com.netflix.spinnaker.rosco.providers.util.PackageNameConverter

class DockerImageNameFactory extends ImageNameFactory {

  @Override
  def buildAppVersionStr(BakeRequest bakeRequest, List<PackageNameConverter.OsPackageName> osPackageNames) {
    String ami_name = bakeRequest.ami_name ?: osPackageNames.first()?.name

    def attributes = bakeRequest.getExtended_attributes()
    String docker_organization = attributes?.get('docker_target_organization') ?: ""
    String docker_image_name = attributes?.get('docker_target_image_name') ?: ami_name

    return [docker_organization, docker_image_name].findAll{it}.join("/")
  }

}
