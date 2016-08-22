package com.netflix.spinnaker.rosco.providers.docker

import com.netflix.spinnaker.rosco.api.BakeRequest
import com.netflix.spinnaker.rosco.providers.util.TestDefaults
import spock.lang.Specification

class DockerImageNameFactorSpec extends Specification implements TestDefaults {
  void "Should build imageName and imageTag without specific docker attributes"() {
    setup:
      def imageNameFactory = new DockerImageNameFactory()
      def bakeRequest = new BakeRequest(
        package_name: "nflx-djangobase-enhanced_0.1-3_all",
        build_number: "12",
        commit_hash: "170cdbd",
        base_os: "ubuntu")
      def osPackages = parseDebOsPackageNames(bakeRequest.package_name)
    when:
      String imageName = imageNameFactory.buildAppVersionStr(bakeRequest, osPackages)
      String imageTag = imageNameFactory.buildImageTag(bakeRequest, osPackages)
      def packagesParameter = imageNameFactory.buildPackagesParameter(DEB_PACKAGE_TYPE, osPackages)
    then:
      imageName == "nflx-djangobase-enhanced"
      imageTag == "nflx-djangobase-enhanced-0.1-h12.170cdbd"
      packagesParameter == "nflx-djangobase-enhanced=0.1-3"
  }

  void "Should build imageName with specific docker attribute"() {
    setup:
      def imageNameFactory = new DockerImageNameFactory()
      def bakeRequest = new BakeRequest(
        package_name: "trojan-banker_0.1-3_all",
        build_number: "12",
        commit_hash: "170cdbd",
        base_os: "ubuntu",
        extended_attributes: [
          docker_target_image_name: "trojan-banker",
          docker_target_organization: "ECorp"
        ]
      )
      def osPackages = parseDebOsPackageNames(bakeRequest.package_name)
    when:
      String imageName = imageNameFactory.buildAppVersionStr(bakeRequest, osPackages)
      String imageTag = imageNameFactory.buildImageTag(bakeRequest, osPackages)
      def packagesParameter = imageNameFactory.buildPackagesParameter(DEB_PACKAGE_TYPE, osPackages)
    then:
      imageName == "ECorp/trojan-banker"
      imageTag == "trojan-banker-0.1-h12.170cdbd"
      packagesParameter == "trojan-banker=0.1-3"
  }
}