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

package com.netflix.spinnaker.rosco.providers.util

import com.netflix.spinnaker.rosco.api.BakeOptions
import com.netflix.spinnaker.rosco.api.BakeRequest
import spock.lang.Specification

import java.time.Clock

class DefaultImageNameFactorySpec extends Specification {

  void "should recognize fully-qualified ubuntu package name"() {
    setup:
      def clockMock = Mock(Clock)
      def imageNameFactory = new DefaultImageNameFactory(clock: clockMock)
      def selectedOptions = new BakeOptions.Selected(baseImage: new BakeOptions.BaseImage(id: "ubuntu", packageType: "DEB"))
      def bakeRequest = new BakeRequest(package_name: "nflx-djangobase-enhanced_0.1-3_all",
                                        build_number: "12",
                                        commit_hash: "170cdbd",
                                        base_os: "ubuntu")

    when:
      def (imageName, appVersionStr, packagesParameter) = imageNameFactory.deriveImageNameAndAppVersion(bakeRequest, selectedOptions)

    then:
      1 * clockMock.millis() >> 123456
      imageName == "nflx-djangobase-enhanced-all-123456-ubuntu"
      appVersionStr == "nflx-djangobase-enhanced-0.1-h12.170cdbd"
      packagesParameter == "nflx-djangobase-enhanced=0.1-3"
  }

  void "should build appversion tag from ubuntu package name even without commit hash"() {
    setup:
      def clockMock = Mock(Clock)
      def imageNameFactory = new DefaultImageNameFactory(clock: clockMock)
      def selectedOptions = new BakeOptions.Selected(baseImage: new BakeOptions.BaseImage(id: "ubuntu", packageType: "DEB"))
      def bakeRequest = new BakeRequest(package_name: "nflx-djangobase-enhanced_0.1-3_all",
                                        build_number: "12",
                                        base_os: "ubuntu")

    when:
      def (imageName, appVersionStr, packagesParameter) = imageNameFactory.deriveImageNameAndAppVersion(bakeRequest, selectedOptions)

    then:
      1 * clockMock.millis() >> 123456
      imageName == "nflx-djangobase-enhanced-all-123456-ubuntu"
      appVersionStr == "nflx-djangobase-enhanced-0.1-h12"
      packagesParameter == "nflx-djangobase-enhanced=0.1-3"
  }

  void "should recognize unqualified ubuntu package name"() {
    setup:
      def clockMock = Mock(Clock)
      def imageNameFactory = new DefaultImageNameFactory(clock: clockMock)
      def selectedOptions = new BakeOptions.Selected(baseImage: new BakeOptions.BaseImage(id: "ubuntu", packageType: "DEB"))
      def bakeRequest = new BakeRequest(package_name: "reno-server",
                                        base_os: "ubuntu")

    when:
      def (imageName, appVersionStr, packagesParameter) = imageNameFactory.deriveImageNameAndAppVersion(bakeRequest, selectedOptions)

    then:
      1 * clockMock.millis() >> 123456
      imageName == "reno-server-all-123456-ubuntu"
      appVersionStr == null
      packagesParameter == "reno-server"
  }

  void "should recognize fully-qualified ubuntu package name plus extra packages"() {
    setup:
      def clockMock = Mock(Clock)
      def imageNameFactory = new DefaultImageNameFactory(clock: clockMock)
      def selectedOptions = new BakeOptions.Selected(baseImage: new BakeOptions.BaseImage(id: "ubuntu", packageType: "DEB"))
      def bakeRequest = new BakeRequest(package_name: "nflx-djangobase-enhanced_0.1-3_all kato redis-server",
                                        build_number: "12",
                                        commit_hash: "170cdbd",
                                        base_os: "ubuntu")

    when:
      def (imageName, appVersionStr, packagesParameter) = imageNameFactory.deriveImageNameAndAppVersion(bakeRequest, selectedOptions)

    then:
      1 * clockMock.millis() >> 123456
      imageName == "nflx-djangobase-enhanced-all-123456-ubuntu"
      appVersionStr == "nflx-djangobase-enhanced-0.1-h12.170cdbd"
      packagesParameter == "nflx-djangobase-enhanced=0.1-3 kato redis-server"
  }

  void "should recognize multiple fully-qualified ubuntu package names but only derive appversion from the first"() {
    setup:
      def clockMock = Mock(Clock)
      def imageNameFactory = new DefaultImageNameFactory(clock: clockMock)
      def selectedOptions = new BakeOptions.Selected(baseImage: new BakeOptions.BaseImage(id: "ubuntu", packageType: "DEB"))
      def bakeRequest = new BakeRequest(package_name: "nflx-djangobase-enhanced_0.1-3_all some-package_0.3-h15.290fcab_all",
                                        build_number: "12",
                                        commit_hash: "170cdbd",
                                        base_os: "ubuntu")

    when:
      def (imageName, appVersionStr, packagesParameter) = imageNameFactory.deriveImageNameAndAppVersion(bakeRequest, selectedOptions)

    then:
      1 * clockMock.millis() >> 123456
      imageName == "nflx-djangobase-enhanced-all-123456-ubuntu"
      appVersionStr == "nflx-djangobase-enhanced-0.1-h12.170cdbd"
      packagesParameter == "nflx-djangobase-enhanced=0.1-3 some-package_0.3-h15.290fcab_all"
  }

  void "should identify version on fully-qualified ubuntu package name without build number and commit hash"() {
    setup:
      def clockMock = Mock(Clock)
      def imageNameFactory = new DefaultImageNameFactory(clock: clockMock)
      def selectedOptions = new BakeOptions.Selected(baseImage: new BakeOptions.BaseImage(id: "ubuntu", packageType: "DEB"))
      def bakeRequest = new BakeRequest(package_name: "nflx-djangobase-enhanced_0.1-3_all",
                                        base_os: "ubuntu")

    when:
      def (imageName, appVersionStr, packagesParameter) = imageNameFactory.deriveImageNameAndAppVersion(bakeRequest, selectedOptions)

    then:
      1 * clockMock.millis() >> 123456
      imageName == "nflx-djangobase-enhanced-all-123456-ubuntu"
      appVersionStr == "nflx-djangobase-enhanced-0.1"
      packagesParameter == "nflx-djangobase-enhanced=0.1-3"
  }

  void "should recognize fully-qualified centos package name"() {
    setup:
      def clockMock = Mock(Clock)
      def imageNameFactory = new DefaultImageNameFactory(clock: clockMock)
      def selectedOptions = new BakeOptions.Selected(baseImage: new BakeOptions.BaseImage(id: "centos", packageType: "RPM"))
      def bakeRequest = new BakeRequest(package_name: "nflx-djangobase-enhanced-0.1-3-all",
                                        build_number: "12",
                                        commit_hash: "170cdbd",
                                        base_os: "centos")

    when:
      def (imageName, appVersionStr, packagesParameter) = imageNameFactory.deriveImageNameAndAppVersion(bakeRequest, selectedOptions)

    then:
      1 * clockMock.millis() >> 123456
      imageName == "nflx-djangobase-enhanced-all-123456-centos"
      appVersionStr == "nflx-djangobase-enhanced-0.1-h12.170cdbd"
      packagesParameter == "nflx-djangobase-enhanced=0.1-3"
  }

  void "should build appversion tag from centos package name even without commit hash"() {
    setup:
      def clockMock = Mock(Clock)
      def imageNameFactory = new DefaultImageNameFactory(clock: clockMock)
      def selectedOptions = new BakeOptions.Selected(baseImage: new BakeOptions.BaseImage(id: "centos", packageType: "RPM"))
      def bakeRequest = new BakeRequest(package_name: "nflx-djangobase-enhanced-0.1-3-all",
                                        build_number: "12",
                                        base_os: "centos")

    when:
      def (imageName, appVersionStr, packagesParameter) = imageNameFactory.deriveImageNameAndAppVersion(bakeRequest, selectedOptions)

    then:
      1 * clockMock.millis() >> 123456
      imageName == "nflx-djangobase-enhanced-all-123456-centos"
      appVersionStr == "nflx-djangobase-enhanced-0.1-h12"
      packagesParameter == "nflx-djangobase-enhanced=0.1-3"
  }

  void "should recognize fully-qualified centos package name plus extra packages"() {
    setup:
      def clockMock = Mock(Clock)
      def imageNameFactory = new DefaultImageNameFactory(clock: clockMock)
      def selectedOptions = new BakeOptions.Selected(baseImage: new BakeOptions.BaseImage(id: "centos", packageType: "RPM"))
      def bakeRequest = new BakeRequest(package_name: "nflx-djangobase-enhanced-0.1-3-all kato redis-server",
                                        build_number: "12",
                                        commit_hash: "170cdbd",
                                        base_os: "centos")

    when:
      def (imageName, appVersionStr, packagesParameter) = imageNameFactory.deriveImageNameAndAppVersion(bakeRequest, selectedOptions)

    then:
      1 * clockMock.millis() >> 123456
      imageName == "nflx-djangobase-enhanced-all-123456-centos"
      appVersionStr == "nflx-djangobase-enhanced-0.1-h12.170cdbd"
      packagesParameter == "nflx-djangobase-enhanced=0.1-3 kato redis-server"
  }

  void "should identify version on fully-qualified centos package name without build number and commit hash"() {
    setup:
      def clockMock = Mock(Clock)
      def imageNameFactory = new DefaultImageNameFactory(clock: clockMock)
      def selectedOptions = new BakeOptions.Selected(baseImage: new BakeOptions.BaseImage(id: "centos", packageType: "RPM"))
      def bakeRequest = new BakeRequest(package_name: "nflx-djangobase-enhanced-0.1-3-all kato redis-server",
                                        base_os: "centos")

    when:
      def (imageName, appVersionStr, packagesParameter) = imageNameFactory.deriveImageNameAndAppVersion(bakeRequest, selectedOptions)

    then:
      1 * clockMock.millis() >> 123456
      imageName == "nflx-djangobase-enhanced-all-123456-centos"
      appVersionStr == "nflx-djangobase-enhanced-0.1"
      packagesParameter == "nflx-djangobase-enhanced=0.1-3 kato redis-server"
  }
}
