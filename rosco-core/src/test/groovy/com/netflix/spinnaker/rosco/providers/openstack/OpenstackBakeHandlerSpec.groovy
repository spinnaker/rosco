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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.rosco.api.Bake
import com.netflix.spinnaker.rosco.api.BakeRequest
import com.netflix.spinnaker.rosco.config.RoscoConfiguration
import com.netflix.spinnaker.rosco.providers.openstack.config.RoscoOpenstackConfiguration
import com.netflix.spinnaker.rosco.providers.util.ImageNameFactory
import com.netflix.spinnaker.rosco.providers.util.PackerCommandFactory
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class OpenstackBakeHandlerSpec extends Specification {

  private static final String PACKAGES_NAME = "kato nflx-djangobase-enhanced_0.1-h12.170cdbd_all mongodb"
  private static final String REGION = "us-east-1"
  private static final String SOURCE_UBUNTU_HVM_IMAGE_NAME = "ami-a123456b"
  private static final String SOURCE_UBUNTU_PV_IMAGE_NAME = "ami-a654321b"
  private static final String SOURCE_TRUSTY_HVM_IMAGE_NAME = "ami-c456789d"
  private static final String SOURCE_OPENSTACK_HVM_IMAGE_NAME = "ami-8fcee4e5"
  private static final String DEBIAN_REPOSITORY = "http://some-debian-repository"
  private static final String YUM_REPOSITORY = "http://some-yum-repository"

  @Shared
  String configDir = "/some/path"

  @Shared
  RoscoOpenstackConfiguration.OpenstackBakeryDefaults openstackBakeryDefaults

  @Shared
  RoscoConfiguration roscoConfiguration

  void setupSpec() {
    def openstackBakeryDefaultsJson = [
      templateFile: "openstack_template.json",
      defaultVirtualizationType: "hvm",
      baseImages: [
        [
          baseImage: [
            id: "ubuntu",
            packageType: "DEB",
          ],
          virtualizationSettings: [
            [
              region: REGION,
              virtualizationType: "hvm",
              instanceType: "t2.micro",
              sourceAmi: SOURCE_UBUNTU_HVM_IMAGE_NAME,
              sshUserName: "ubuntu"
            ],
            [
              region: REGION,
              virtualizationType: "pv",
              instanceType: "m3.medium",
              sourceAmi: SOURCE_UBUNTU_PV_IMAGE_NAME,
              sshUserName: "ubuntu"
            ]
          ]
        ],
        [
          baseImage: [
            id: "trusty",
            packageType: "DEB",
          ],
          virtualizationSettings: [
            [
              region: REGION,
              virtualizationType: "hvm",
              instanceType: "t2.micro",
              sourceAmi: SOURCE_TRUSTY_HVM_IMAGE_NAME,
              sshUserName: "ubuntu"
            ]
          ]
        ],
        [
          baseImage: [
           id: "amzn",
           packageType: "RPM",
          ],
          virtualizationSettings: [
            [
              region: REGION,
              virtualizationType: "hvm",
              instanceType: "t2.micro",
              sourceAmi: SOURCE_OPENSTACK_HVM_IMAGE_NAME,
              sshUserName: "ec2-user"
            ]
          ]
        ]
      ]
    ]

    openstackBakeryDefaults = new ObjectMapper().convertValue(openstackBakeryDefaultsJson, RoscoOpenstackConfiguration.OpenstackBakeryDefaults)
  }

  void 'can identify amazon-ebs builds'() {
    setup:
      @Subject
      OpenstackBakeHandler openstackBakeHandler = new OpenstackBakeHandler(openstackBakeryDefaults: openstackBakeryDefaults)

    when:
      def logsContent =
        "    openstack-ebs: Processing triggers for libc-bin ...\n"

      Boolean Producer = openstackBakeHandler.isProducerOf(logsContent)

    then:
      Producer == true
  }

  void 'can identify amazon-chroot builds'() {
    setup:
      @Subject
      OpenstackBakeHandler openstackBakeHandler = new OpenstackBakeHandler(openstackBakeryDefaults: openstackBakeryDefaults)

    when:
      def logsContent =
        "    openstack-chroot: Processing triggers for libc-bin ...\n"

      Boolean openstackProducer = openstackBakeHandler.isProducerOf(logsContent)

    then:
    openstackProducer == true
  }

  void 'rejects non amazon builds'() {
    setup:
      @Subject
      OpenstackBakeHandler openstackBakeHandler = new OpenstackBakeHandler(openstackBakeryDefaults: openstackBakeryDefaults)

    when:
      def logsContent =
        "    somesystem-thing: Processing triggers for libc-bin ...\n"

      Boolean openstackProducer = openstackBakeHandler.isProducerOf(logsContent)

    then:
      openstackProducer == false
  }

  void 'can scrape packer logs for image name'() {
    setup:
      @Subject
      OpenstackBakeHandler openstackBakeHandler = new OpenstackBakeHandler(openstackBakeryDefaults: openstackBakeryDefaults)

    when:
      def logsContent =
        "    amazon-ebs: Processing triggers for libc-bin ...\n" +
        "    amazon-ebs: ldconfig deferred processing now taking place\n" +
        "==> amazon-ebs: Stopping the source instance...\n" +
        "==> amazon-ebs: Waiting for the instance to stop...\n" +
        "==> amazon-ebs: Creating the AMI: kato-x8664-1422459898853-ubuntu\n" +
        "    amazon-ebs: AMI: ami-2c014644\n" +
        "==> amazon-ebs: Waiting for AMI to become ready...\n" +
        "==> amazon-ebs: Terminating the source AWS instance...\n" +
        "==> amazon-ebs: Deleting temporary security group...\n" +
        "==> amazon-ebs: Deleting temporary keypair...\n" +
        "Build 'amazon-ebs' finished.\n" +
        "\n" +
        "==> Builds finished. The artifacts of successful builds are:\n" +
        "--> amazon-ebs: AMIs were created:\n" +
        "\n" +
        "us-east-1: ami-2c014644"

      Bake bake = openstackBakeHandler.scrapeCompletedBakeResults(REGION, "123", logsContent)

    then:
      with (bake) {
        id == "123"
        ami == "ami-2c014644"
        image_name == "kato-x8664-1422459898853-ubuntu"
      }
  }

  void 'can scrape packer (amazon-chroot) logs for image name'() {
    setup:
      @Subject
      OpenstackBakeHandler openstackBakeHandler = new OpenstackBakeHandler(openstackBakeryDefaults: openstackBakeryDefaults)

    when:
      def logsContent =
        "    amazon-chroot: Processing triggers for libc-bin ...\n" +
        "    amazon-chroot: ldconfig deferred processing now taking place\n" +
        "==> amazon-chroot: Stopping the source instance...\n" +
        "==> amazon-chroot: Waiting for the instance to stop...\n" +
        "==> amazon-chroot: Creating the AMI: kato-x8664-1422459898853-ubuntu\n" +
        "    amazon-chroot: AMI: ami-2c014644\n" +
        "==> amazon-chroot: Waiting for AMI to become ready...\n" +
        "==> amazon-chroot: Terminating the source AWS instance...\n" +
        "==> amazon-chroot: Deleting temporary security group...\n" +
        "==> amazon-chroot: Deleting temporary keypair...\n" +
        "Build 'amazon-chroot' finished.\n" +
        "\n" +
        "==> Builds finished. The artifacts of successful builds are:\n" +
        "--> amazon-chroot: AMIs were created:\n" +
        "\n" +
        "us-east-1: ami-2c014644"

      Bake bake = openstackBakeHandler.scrapeCompletedBakeResults(REGION, "123", logsContent)

    then:
      with (bake) {
        id == "123"
        ami == "ami-2c014644"
        image_name == "kato-x8664-1422459898853-ubuntu"
      }
  }


  void 'scraping returns null for missing image id'() {
    setup:
      @Subject
      OpenstackBakeHandler openstackBakeHandler = new OpenstackBakeHandler(openstackBakeryDefaults: openstackBakeryDefaults)

    when:
      def logsContent =
        "    amazon-ebs: Processing triggers for libc-bin ...\n" +
        "    amazon-ebs: ldconfig deferred processing now taking place\n" +
        "==> amazon-ebs: Stopping the source instance...\n" +
        "==> amazon-ebs: Waiting for the instance to stop...\n" +
        "==> amazon-ebs: Creating the AMI: kato-x8664-1422459898853-ubuntu\n" +
        "    amazon-ebs: AMI: ami-2c014644\n" +
        "==> amazon-ebs: Waiting for AMI to become ready...\n" +
        "==> amazon-ebs: Terminating the source AWS instance...\n" +
        "==> amazon-ebs: Deleting temporary security group...\n" +
        "==> amazon-ebs: Deleting temporary keypair...\n" +
        "Build 'amazon-ebs' finished.\n" +
        "\n" +
        "==> Builds finished. The artifacts of successful builds are:\n" +
        "--> amazon-ebs: AMIs were created:\n"

      Bake bake = openstackBakeHandler.scrapeCompletedBakeResults(REGION, "123", logsContent)

    then:
      with (bake) {
        id == "123"
        !ami
        image_name == "kato-x8664-1422459898853-ubuntu"
      }
  }

  void 'scraping returns null for missing image name'() {
    setup:
      @Subject
      OpenstackBakeHandler openstackBakeHandler = new OpenstackBakeHandler(openstackBakeryDefaults: openstackBakeryDefaults)

    when:
      def logsContent =
          "    amazon-ebs: Processing triggers for libc-bin ...\n" +
          "    amazon-ebs: ldconfig deferred processing now taking place\n" +
          "==> amazon-ebs: Stopping the source instance...\n" +
          "==> amazon-ebs: Waiting for the instance to stop...\n"

      Bake bake = openstackBakeHandler.scrapeCompletedBakeResults(REGION, "123", logsContent)

    then:
      with (bake) {
        id == "123"
        !ami
        !image_name
      }
  }

  void 'produces packer command with all required parameters for ubuntu, using default vm type'() {
    setup:
      def imageNameFactoryMock = Mock(ImageNameFactory)
      def packerCommandFactoryMock = Mock(PackerCommandFactory)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGES_NAME,
                                        base_os: "ubuntu",
                                        vm_type: BakeRequest.VmType.hvm,
                                        cloud_provider_type: BakeRequest.CloudProviderType.openstack)
      def targetImageName = "kato-x8664-timestamp-ubuntu"
      def parameterMap = [
        openstack_region: REGION,
        openstack_ssh_username: "ubuntu",
        openstack_instance_type: "t2.micro",
        openstack_source_ami: SOURCE_UBUNTU_HVM_IMAGE_NAME,
        openstack_target_ami: targetImageName,
        repository: DEBIAN_REPOSITORY,
        package_type: BakeRequest.PackageType.DEB.packageType,
        packages: PACKAGES_NAME,
        configDir: configDir
      ]

      @Subject
      OpenstackBakeHandler openstackBakeHandler = new OpenstackBakeHandler(configDir: configDir,
                                                         openstackBakeryDefaults: openstackBakeryDefaults,
                                                         imageNameFactory: imageNameFactoryMock,
                                                         packerCommandFactory: packerCommandFactoryMock,
                                                         debianRepository: DEBIAN_REPOSITORY)

    when:
      openstackBakeHandler.producePackerCommand(REGION, bakeRequest)

    then:
      1 * imageNameFactoryMock.deriveImageNameAndAppVersion(bakeRequest, _) >> [targetImageName, null, PACKAGES_NAME]
      1 * packerCommandFactoryMock.buildPackerCommand("", parameterMap, "$configDir/$openstackBakeryDefaults.templateFile")
  }

  void 'produces packer command with all required parameters for amzn, using default vm type'() {
    setup:
      def imageNameFactoryMock = Mock(ImageNameFactory)
      def packerCommandFactoryMock = Mock(PackerCommandFactory)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGES_NAME,
                                        base_os: "amzn",
                                        vm_type: BakeRequest.VmType.hvm,
                                        cloud_provider_type: BakeRequest.CloudProviderType.openstack)
      def targetImageName = "kato-x8664-timestamp-amzn"
      def parameterMap = [
        openstack_region: REGION,
        openstack_ssh_username: "ec2-user",
        openstack_instance_type: "t2.micro",
        openstack_source_ami: SOURCE_OPENSTACK_HVM_IMAGE_NAME,
        openstack_target_ami: targetImageName,
        repository: YUM_REPOSITORY,
        package_type: BakeRequest.PackageType.RPM.packageType,
        packages: PACKAGES_NAME,
        configDir: configDir
      ]

      @Subject
      OpenstackBakeHandler openstackBakeHandler = new OpenstackBakeHandler(configDir: configDir,
                                                         openstackBakeryDefaults: openstackBakeryDefaults,
                                                         imageNameFactory: imageNameFactoryMock,
                                                         packerCommandFactory: packerCommandFactoryMock,
                                                         yumRepository: YUM_REPOSITORY)

    when:
      openstackBakeHandler.producePackerCommand(REGION, bakeRequest)

    then:
      1 * imageNameFactoryMock.deriveImageNameAndAppVersion(bakeRequest, _) >> [targetImageName, null, PACKAGES_NAME]
      1 * packerCommandFactoryMock.buildPackerCommand("", parameterMap, "$configDir/$openstackBakeryDefaults.templateFile")
  }

  void 'produces packer command with all required parameters for amzn, with sudo'() {
    setup:
      def openstackBakeryDefaultsJson = [
        templateFile: "openstack-chroot.json",
        defaultVirtualizationType: "hvm",
        baseImages: [
          [
            baseImage: [
              id: "amzn",
              packageType: "RPM",
            ],
            virtualizationSettings: [
              [
                region: REGION,
                virtualizationType: "hvm",
                instanceType: "t2.micro",
                sourceAmi: SOURCE_OPENSTACK_HVM_IMAGE_NAME,
                sshUserName: "ec2-user"
              ]
            ]
          ]
        ]
      ]
      RoscoOpenstackConfiguration.OpenstackBakeryDefaults localAwsBakeryDefaults = new ObjectMapper().convertValue(openstackBakeryDefaultsJson, RoscoOpenstackConfiguration.OpenstackBakeryDefaults)

      def imageNameFactoryMock = Mock(ImageNameFactory)
      def packerCommandFactoryMock = Mock(PackerCommandFactory)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
        package_name: PACKAGES_NAME,
        base_os: "amzn",
        vm_type: BakeRequest.VmType.hvm,
        cloud_provider_type: BakeRequest.CloudProviderType.openstack)
      def targetImageName = "kato-x8664-timestamp-amzn"
      def parameterMap = [
        openstack_region: REGION,
        openstack_ssh_username: "ec2-user",
        openstack_instance_type: "t2.micro",
        openstack_source_ami: SOURCE_OPENSTACK_HVM_IMAGE_NAME,
        openstack_target_ami: targetImageName,
        repository: YUM_REPOSITORY,
        package_type: BakeRequest.PackageType.RPM.packageType,
        packages: PACKAGES_NAME,
        configDir: configDir
      ]

      @Subject
      OpenstackBakeHandler openstackBakeHandler = new OpenstackBakeHandler(configDir: configDir,
        openstackBakeryDefaults: localAwsBakeryDefaults,
        imageNameFactory: imageNameFactoryMock,
        packerCommandFactory: packerCommandFactoryMock,
        templatesNeedingRoot: [ "openstack-chroot.json" ],
        yumRepository: YUM_REPOSITORY)

    when:
      openstackBakeHandler.producePackerCommand(REGION, bakeRequest)

    then:
      1 * imageNameFactoryMock.deriveImageNameAndAppVersion(bakeRequest, _) >> [targetImageName, null, PACKAGES_NAME]
      1 * packerCommandFactoryMock.buildPackerCommand("sudo", parameterMap, "$configDir/openstack-chroot.json")
  }

  void 'produces packer command with all required parameters for ubuntu, using default vm type, and overriding base ami'() {
    setup:
      def imageNameFactoryMock = Mock(ImageNameFactory)
      def packerCommandFactoryMock = Mock(PackerCommandFactory)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGES_NAME,
                                        base_os: "ubuntu",
                                        base_ami: "ami-12345678",
                                        vm_type: BakeRequest.VmType.hvm,
                                        cloud_provider_type: BakeRequest.CloudProviderType.openstack)
      def targetImageName = "kato-x8664-timestamp-ubuntu"
      def parameterMap = [
        openstack_region: REGION,
        openstack_ssh_username: "ubuntu",
        openstack_instance_type: "t2.micro",
        openstack_source_ami: "ami-12345678",
        openstack_target_ami: targetImageName,
        repository: DEBIAN_REPOSITORY,
        package_type: BakeRequest.PackageType.DEB.packageType,
        packages: PACKAGES_NAME,
        configDir: configDir
      ]

      @Subject
      OpenstackBakeHandler openstackBakeHandler = new OpenstackBakeHandler(configDir: configDir,
                                                         openstackBakeryDefaults: openstackBakeryDefaults,
                                                         imageNameFactory: imageNameFactoryMock,
                                                         packerCommandFactory: packerCommandFactoryMock,
                                                         debianRepository: DEBIAN_REPOSITORY)

    when:
      openstackBakeHandler.producePackerCommand(REGION, bakeRequest)

    then:
      1 * imageNameFactoryMock.deriveImageNameAndAppVersion(bakeRequest, _) >> [targetImageName, null, PACKAGES_NAME]
      1 * packerCommandFactoryMock.buildPackerCommand("", parameterMap, "$configDir/$openstackBakeryDefaults.templateFile")
  }

  void 'produces packer command with all required parameters for ubuntu, using explicit vm type'() {
    setup:
      def imageNameFactoryMock = Mock(ImageNameFactory)
      def packerCommandFactoryMock = Mock(PackerCommandFactory)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGES_NAME,
                                        base_os: "ubuntu",
                                        vm_type: BakeRequest.VmType.pv,
                                        cloud_provider_type: BakeRequest.CloudProviderType.openstack)
      def targetImageName = "kato-x8664-timestamp-ubuntu"
      def parameterMap = [
        openstack_region: REGION,
        openstack_ssh_username: "ubuntu",
        openstack_instance_type: "m3.medium",
        openstack_source_ami: SOURCE_UBUNTU_PV_IMAGE_NAME,
        openstack_target_ami: targetImageName,
        repository: DEBIAN_REPOSITORY,
        package_type: BakeRequest.PackageType.DEB.packageType,
        packages: PACKAGES_NAME,
        configDir: configDir
      ]

      @Subject
      OpenstackBakeHandler openstackBakeHandler = new OpenstackBakeHandler(configDir: configDir,
                                                         openstackBakeryDefaults: openstackBakeryDefaults,
                                                         imageNameFactory: imageNameFactoryMock,
                                                         packerCommandFactory: packerCommandFactoryMock,
                                                         debianRepository: DEBIAN_REPOSITORY)

    when:
      openstackBakeHandler.producePackerCommand(REGION, bakeRequest)

    then:
      1 * imageNameFactoryMock.deriveImageNameAndAppVersion(bakeRequest, _) >> [targetImageName, null, PACKAGES_NAME]
      1 * packerCommandFactoryMock.buildPackerCommand("", parameterMap, "$configDir/$openstackBakeryDefaults.templateFile")
  }

  void 'produces packer command with all required parameters for ubuntu, using explicit vm type, and overriding template filename'() {
    setup:
      def imageNameFactoryMock = Mock(ImageNameFactory)
      def packerCommandFactoryMock = Mock(PackerCommandFactory)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGES_NAME,
                                        base_os: "ubuntu",
                                        vm_type: BakeRequest.VmType.pv,
                                        cloud_provider_type: BakeRequest.CloudProviderType.openstack,
                                        template_file_name: "somePackerTemplate.json")
      def targetImageName = "kato-x8664-timestamp-ubuntu"
      def parameterMap = [
        openstack_region: REGION,
        openstack_ssh_username: "ubuntu",
        openstack_instance_type: "m3.medium",
        openstack_source_ami: SOURCE_UBUNTU_PV_IMAGE_NAME,
        openstack_target_ami: targetImageName,
        repository: DEBIAN_REPOSITORY,
        package_type: BakeRequest.PackageType.DEB.packageType,
        packages: PACKAGES_NAME,
        configDir: configDir
      ]

      @Subject
      OpenstackBakeHandler openstackBakeHandler = new OpenstackBakeHandler(configDir: configDir,
                                                         openstackBakeryDefaults: openstackBakeryDefaults,
                                                         imageNameFactory: imageNameFactoryMock,
                                                         packerCommandFactory: packerCommandFactoryMock,
                                                         debianRepository: DEBIAN_REPOSITORY)

    when:
      openstackBakeHandler.producePackerCommand(REGION, bakeRequest)

    then:
      1 * imageNameFactoryMock.deriveImageNameAndAppVersion(bakeRequest, _) >> [targetImageName, null, PACKAGES_NAME]
      1 * packerCommandFactoryMock.buildPackerCommand("", parameterMap, "$configDir/somePackerTemplate.json")
  }

  void 'produces packer command with all required parameters for ubuntu, using explicit vm type, and adding extended attributes'() {
    setup:
      def imageNameFactoryMock = Mock(ImageNameFactory)
      def packerCommandFactoryMock = Mock(PackerCommandFactory)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGES_NAME,
                                        base_os: "ubuntu",
                                        vm_type: BakeRequest.VmType.pv,
                                        cloud_provider_type: BakeRequest.CloudProviderType.openstack,
                                        extended_attributes: [someAttr1: "someValue1", someAttr2: "someValue2"])
      def targetImageName = "kato-x8664-timestamp-ubuntu"
      def parameterMap = [
        openstack_region: REGION,
        openstack_ssh_username: "ubuntu",
        openstack_instance_type: "m3.medium",
        openstack_source_ami: SOURCE_UBUNTU_PV_IMAGE_NAME,
        openstack_target_ami: targetImageName,
        repository: DEBIAN_REPOSITORY,
        package_type: BakeRequest.PackageType.DEB.packageType,
        packages: PACKAGES_NAME,
        configDir: configDir,
        someAttr1: "someValue1",
        someAttr2: "someValue2"
      ]

      @Subject
      OpenstackBakeHandler openstackBakeHandler = new OpenstackBakeHandler(configDir: configDir,
                                                         openstackBakeryDefaults: openstackBakeryDefaults,
                                                         imageNameFactory: imageNameFactoryMock,
                                                         packerCommandFactory: packerCommandFactoryMock,
                                                         debianRepository: DEBIAN_REPOSITORY)

    when:
      openstackBakeHandler.producePackerCommand(REGION, bakeRequest)

    then:
      1 * imageNameFactoryMock.deriveImageNameAndAppVersion(bakeRequest, _) >> [targetImageName, null, PACKAGES_NAME]
      1 * packerCommandFactoryMock.buildPackerCommand("", parameterMap, "$configDir/$openstackBakeryDefaults.templateFile")
  }

  void 'produces packer command with all required parameters for trusty, using explicit vm type'() {
    setup:
      def imageNameFactoryMock = Mock(ImageNameFactory)
      def packerCommandFactoryMock = Mock(PackerCommandFactory)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGES_NAME,
                                        base_os: "trusty",
                                        vm_type: BakeRequest.VmType.hvm,
                                        cloud_provider_type: BakeRequest.CloudProviderType.openstack)
      def targetImageName = "kato-x8664-timestamp-trusty"
      def parameterMap = [
        openstack_region: REGION,
        openstack_ssh_username: "ubuntu",
        openstack_instance_type: "t2.micro",
        openstack_source_ami: SOURCE_TRUSTY_HVM_IMAGE_NAME,
        openstack_target_ami: targetImageName,
        repository: DEBIAN_REPOSITORY,
        package_type: BakeRequest.PackageType.DEB.packageType,
        packages: PACKAGES_NAME,
        configDir: configDir
      ]

      @Subject
      OpenstackBakeHandler openstackBakeHandler = new OpenstackBakeHandler(configDir: configDir,
                                                         openstackBakeryDefaults: openstackBakeryDefaults,
                                                         imageNameFactory: imageNameFactoryMock,
                                                         packerCommandFactory: packerCommandFactoryMock,
                                                         debianRepository: DEBIAN_REPOSITORY)

    when:
      openstackBakeHandler.producePackerCommand(REGION, bakeRequest)

    then:
      1 * imageNameFactoryMock.deriveImageNameAndAppVersion(bakeRequest, _) >> [targetImageName, null, PACKAGES_NAME]
      1 * packerCommandFactoryMock.buildPackerCommand("", parameterMap, "$configDir/$openstackBakeryDefaults.templateFile")
  }

  void 'produces packer command with all required parameters including appversion and build_host for trusty'() {
    setup:
      def imageNameFactoryMock = Mock(ImageNameFactory)
      def packerCommandFactoryMock = Mock(PackerCommandFactory)
      def fullyQualifiedPackageName = "nflx-djangobase-enhanced_0.1-h12.170cdbd_all"
      def appVersionStr = "nflx-djangobase-enhanced-0.1-170cdbd.h12"
      def buildHost = "http://some-build-server:8080"
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: fullyQualifiedPackageName,
                                        base_os: "trusty",
                                        vm_type: BakeRequest.VmType.hvm,
                                        build_host: buildHost,
                                        cloud_provider_type: BakeRequest.CloudProviderType.openstack)
      def targetImageName = "kato-x8664-timestamp-trusty"
      def parameterMap = [
        openstack_region: REGION,
        openstack_ssh_username: "ubuntu",
        openstack_instance_type: "t2.micro",
        openstack_source_ami: SOURCE_TRUSTY_HVM_IMAGE_NAME,
        openstack_target_ami: targetImageName,
        repository: DEBIAN_REPOSITORY,
        package_type: BakeRequest.PackageType.DEB.packageType,
        packages: fullyQualifiedPackageName,
        configDir: configDir,
        appversion: appVersionStr,
        build_host: buildHost
      ]

      @Subject
      OpenstackBakeHandler openstackBakeHandler = new OpenstackBakeHandler(configDir: configDir,
                                                         openstackBakeryDefaults: openstackBakeryDefaults,
                                                         imageNameFactory: imageNameFactoryMock,
                                                         packerCommandFactory: packerCommandFactoryMock,
                                                         debianRepository: DEBIAN_REPOSITORY)

    when:
      openstackBakeHandler.producePackerCommand(REGION, bakeRequest)

    then:
      1 * imageNameFactoryMock.deriveImageNameAndAppVersion(bakeRequest, _) >>
        [targetImageName, appVersionStr, fullyQualifiedPackageName]
      1 * packerCommandFactoryMock.buildPackerCommand("", parameterMap, "$configDir/$openstackBakeryDefaults.templateFile")
  }

  void 'produces packer command with all required parameters including upgrade'() {
    setup:
      def imageNameFactoryMock = Mock(ImageNameFactory)
      def packerCommandFactoryMock = Mock(PackerCommandFactory)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGES_NAME,
                                        base_os: "ubuntu",
                                        vm_type: BakeRequest.VmType.hvm,
                                        cloud_provider_type: BakeRequest.CloudProviderType.openstack,
                                        upgrade: true)
      def targetImageName = "kato-x8664-timestamp-ubuntu"
      def parameterMap = [
        openstack_region: REGION,
        openstack_ssh_username: "ubuntu",
        openstack_instance_type: "t2.micro",
        openstack_source_ami: SOURCE_UBUNTU_HVM_IMAGE_NAME,
        openstack_target_ami: targetImageName,
        repository: DEBIAN_REPOSITORY,
        package_type: BakeRequest.PackageType.DEB.packageType,
        packages: PACKAGES_NAME,
        upgrade: true,
        configDir: configDir
      ]

      @Subject
      OpenstackBakeHandler openstackBakeHandler = new OpenstackBakeHandler(configDir: configDir,
                                                         openstackBakeryDefaults: openstackBakeryDefaults,
                                                         imageNameFactory: imageNameFactoryMock,
                                                         packerCommandFactory: packerCommandFactoryMock,
                                                         debianRepository: DEBIAN_REPOSITORY)

    when:
      openstackBakeHandler.producePackerCommand(REGION, bakeRequest)

    then:
      1 * imageNameFactoryMock.deriveImageNameAndAppVersion(bakeRequest, _) >> [targetImageName, null, PACKAGES_NAME]
      1 * packerCommandFactoryMock.buildPackerCommand("", parameterMap, "$configDir/$openstackBakeryDefaults.templateFile")
  }

  void 'throws exception when virtualization settings are not found for specified operating system'() {
    setup:
      def imageNameFactoryMock = Mock(ImageNameFactory)
      def packerCommandFactoryMock = Mock(PackerCommandFactory)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGES_NAME,
                                        base_os: "centos",
                                        vm_type: BakeRequest.VmType.hvm,
                                        cloud_provider_type: BakeRequest.CloudProviderType.openstack)

      @Subject
      OpenstackBakeHandler openstackBakeHandler = new OpenstackBakeHandler(openstackBakeryDefaults: openstackBakeryDefaults,
                                                         imageNameFactory: imageNameFactoryMock,
                                                         packerCommandFactory: packerCommandFactoryMock,
                                                         debianRepository: DEBIAN_REPOSITORY)

    when:
      openstackBakeHandler.producePackerCommand(REGION, bakeRequest)

    then:
      IllegalArgumentException e = thrown()
      e.message == "No virtualization settings found for 'centos'."
  }

  void 'throws exception when virtualization settings are not found for specified region, operating system, and vm type'() {
    setup:
      def imageNameFactoryMock = Mock(ImageNameFactory)
      def packerCommandFactoryMock = Mock(PackerCommandFactory)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGES_NAME,
                                        base_os: "trusty",
                                        vm_type: BakeRequest.VmType.pv,
                                        cloud_provider_type: BakeRequest.CloudProviderType.openstack)

      @Subject
      OpenstackBakeHandler openstackBakeHandler = new OpenstackBakeHandler(openstackBakeryDefaults: openstackBakeryDefaults,
                                                         imageNameFactory: imageNameFactoryMock,
                                                         packerCommandFactory: packerCommandFactoryMock,
                                                         debianRepository: DEBIAN_REPOSITORY)

    when:
      openstackBakeHandler.producePackerCommand(REGION, bakeRequest)

    then:
      IllegalArgumentException e = thrown()
      e.message == "No virtualization settings found for region 'us-east-1', operating system 'trusty', and vm type 'pv'."
  }

  void 'produce a default AWS bakeKey without base ami'() {
    setup:
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGES_NAME,
                                        base_os: "centos",
                                        vm_type: BakeRequest.VmType.hvm,
                                        cloud_provider_type: BakeRequest.CloudProviderType.openstack)

      @Subject
      OpenstackBakeHandler openstackBakeHandler = new OpenstackBakeHandler(openstackBakeryDefaults: openstackBakeryDefaults)

    when:
      String bakeKey = openstackBakeHandler.produceBakeKey(REGION, bakeRequest)

    then:
      bakeKey == "bake:openstack:centos:kato|nflx-djangobase-enhanced_0.1-h12.170cdbd_all|mongodb:us-east-1:hvm:enhancedNWDisabled"
  }

  @Unroll
  void 'produce a default AWS bakeKey without base ami, even when no packages are specified'() {
    setup:
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: packageName,
                                        base_os: "centos",
                                        vm_type: BakeRequest.VmType.hvm,
                                        cloud_provider_type: BakeRequest.CloudProviderType.openstack)

      @Subject
      OpenstackBakeHandler openstackBakeHandler = new OpenstackBakeHandler(openstackBakeryDefaults: openstackBakeryDefaults)

    when:
      String bakeKey = openstackBakeHandler.produceBakeKey(REGION, bakeRequest)

    then:
      bakeKey == "bake:openstack:centos::us-east-1:hvm:enhancedNWDisabled"

    where:
      packageName << [null, ""]
  }

  void 'produce a default AWS bakeKey with base ami'() {
    setup:
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGES_NAME,
                                        base_os: "centos",
                                        vm_type: BakeRequest.VmType.hvm,
                                        cloud_provider_type: BakeRequest.CloudProviderType.openstack,
                                        base_ami: "ami-123456")

      @Subject
      OpenstackBakeHandler openstackBakeHandler = new OpenstackBakeHandler(openstackBakeryDefaults: openstackBakeryDefaults)

    when:
      String bakeKey = openstackBakeHandler.produceBakeKey(REGION, bakeRequest)

    then:
      bakeKey == "bake:openstack:centos:ami-123456:kato|nflx-djangobase-enhanced_0.1-h12.170cdbd_all|mongodb:us-east-1:hvm:enhancedNWDisabled"
  }

  void 'produce a default AWS bakeKey with enhanced network enabled'() {
    setup:
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGES_NAME,
                                        base_os: "centos",
                                        vm_type: BakeRequest.VmType.hvm,
                                        cloud_provider_type: BakeRequest.CloudProviderType.openstack,
                                        enhanced_networking: true)

      @Subject
      OpenstackBakeHandler openstackBakeHandler = new OpenstackBakeHandler(openstackBakeryDefaults: openstackBakeryDefaults)

    when:
      String bakeKey = openstackBakeHandler.produceBakeKey(REGION, bakeRequest)

    then:
      bakeKey == "bake:openstack:centos:kato|nflx-djangobase-enhanced_0.1-h12.170cdbd_all|mongodb:us-east-1:hvm:enhancedNWEnabled"
  }

  void 'produce a default AWS bakeKey with ami name'() {
    setup:
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGES_NAME,
                                        base_os: "centos",
                                        vm_type: BakeRequest.VmType.hvm,
                                        cloud_provider_type: BakeRequest.CloudProviderType.openstack,
                                        ami_name: "kato-app")
      @Subject
      OpenstackBakeHandler openstackBakeHandler = new OpenstackBakeHandler(openstackBakeryDefaults: openstackBakeryDefaults)

    when:
      String bakeKey = openstackBakeHandler.produceBakeKey(REGION, bakeRequest)

    then:
      bakeKey == "bake:openstack:centos:kato-app:kato|nflx-djangobase-enhanced_0.1-h12.170cdbd_all|mongodb:us-east-1:hvm:enhancedNWDisabled"
  }

  void 'do not consider ami suffix when composing bake key'() {
    setup:
      def bakeRequest1 = new BakeRequest(user: "someuser@gmail.com",
                                         package_name: PACKAGES_NAME,
                                         base_os: "centos",
                                         vm_type: BakeRequest.VmType.hvm,
                                         cloud_provider_type: BakeRequest.CloudProviderType.openstack,
                                         ami_suffix: "1.0")
      def bakeRequest2 = new BakeRequest(user: "someuser@gmail.com",
                                         package_name: PACKAGES_NAME,
                                         base_os: "centos",
                                         vm_type: BakeRequest.VmType.hvm,
                                         cloud_provider_type: BakeRequest.CloudProviderType.openstack,
                                         ami_suffix: "2.0")
      @Subject
      OpenstackBakeHandler openstackBakeHandler = new OpenstackBakeHandler(openstackBakeryDefaults: openstackBakeryDefaults)

    when:
      String bakeKey1 = openstackBakeHandler.produceBakeKey(REGION, bakeRequest1)
      String bakeKey2 = openstackBakeHandler.produceBakeKey(REGION, bakeRequest2)

    then:
      bakeKey1 == "bake:openstack:centos:kato|nflx-djangobase-enhanced_0.1-h12.170cdbd_all|mongodb:us-east-1:hvm:enhancedNWDisabled"
      bakeKey2 == bakeKey1
  }
}
