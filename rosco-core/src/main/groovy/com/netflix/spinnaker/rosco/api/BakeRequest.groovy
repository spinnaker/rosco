/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.rosco.api

import com.fasterxml.jackson.annotation.JsonProperty
import com.google.gson.annotations.SerializedName
import groovy.transform.CompileStatic
import groovy.transform.Immutable
import io.swagger.annotations.ApiModelProperty

/**
 * A request to bake a new machine image.
 *
 * @see BakeryController#createBake(String, BakeRequest, String)
 */
@Immutable(copyWith = true)
@CompileStatic
class BakeRequest {

  String user
  @ApiModelProperty("The package(s) to install") @JsonProperty("package") @SerializedName("package")
  String package_name
  @ApiModelProperty("The CI server")
  String build_host
  @ApiModelProperty("The CI job")
  String job
  @ApiModelProperty("The CI build number")
  String build_number
  @ApiModelProperty("The commit hash of the CI build")
  String commit_hash
  @ApiModelProperty("The target platform")
  CloudProviderType cloud_provider_type
  Label base_label
  @ApiModelProperty("The named base image to resolve from rosco's configuration")
  String base_os
  String base_name
  @ApiModelProperty("The explicit machine image to use, instead of resolving one from rosco's configuration")
  String base_ami
  VmType vm_type
  StoreType store_type
  Boolean enhanced_networking
  String ami_name
  String ami_suffix
  Boolean upgrade
  String instance_type

  @ApiModelProperty("The explicit packer template to use, instead of resolving one from rosco's configuration")
  String template_file_name
  @ApiModelProperty("A map of key/value pairs to add to the packer command")
  Map extended_attributes

  static enum CloudProviderType {
    aws, azure, docker, gce, openstack
  }

  static enum Label {
    release, candidate, previous, unstable, foundation
  }

  static enum PackageType {
    RPM('rpm', '-'),
    DEB('deb', '=')

    private final String packageType
    private final String versionDelimiter

    private PackageType(String packageType, String versionDelimiter) {
      this.packageType = packageType
      this.versionDelimiter = versionDelimiter
    }

    String getPackageType() {
      return this.packageType
    }

    String getVersionDelimiter() {
      return this.versionDelimiter
    }
  }

  static enum VmType {
    pv, hvm
  }

  static enum StoreType {
    ebs, s3, docker
  }
}

