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

import com.netflix.spinnaker.rosco.config.RoscoConfiguration
import com.netflix.spinnaker.rosco.providers.openstack.config.RoscoOpenstackConfiguration
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class OpenstackBakeHandlerSpec extends Specification {

  @Shared
  String configDir = "/some/path"

  @Shared
  RoscoOpenstackConfiguration.OpenstackBakeryDefaults openstackBakeryDefaults

  @Shared
  RoscoConfiguration roscoConfiguration

  void setupSpec() {
    //TODO
  }

  void 'can identify openstack-ebs builds'() {
    //TODO
  }

  void 'can identify openstack-chroot builds'() {
    //TODO
  }

  void 'rejects non amazon builds'() {
    //TODO
  }

  void 'can scrape packer logs for image name'() {
    //TODO
  }

  void 'can scrape packer (openstack-chroot) logs for image name'() {
    //TODO
  }


  void 'scraping returns null for missing image id'() {
    //TODO
  }

  void 'scraping returns null for missing image name'() {
    //TODO
  }

  void 'produces packer command with all required parameters for ubuntu, using default vm type'() {
    //TODO
  }

  void 'produces packer command with all required parameters for amzn, using default vm type'() {
    //TODO
  }

  void 'produces packer command with all required parameters for amzn, with sudo'() {
    //TODO
  }

  void 'produces packer command with all required parameters for ubuntu, using default vm type, and overriding base ami'() {
    //TODO
  }

  void 'produces packer command with all required parameters for ubuntu, using explicit vm type'() {
    //TODO
  }

  void 'produces packer command with all required parameters for ubuntu, using explicit vm type, and overriding template filename'() {
    //TODO
  }

  void 'produces packer command with all required parameters for ubuntu, using explicit vm type, and adding extended attributes'() {
    //TODO
  }

  void 'produces packer command with all required parameters for trusty, using explicit vm type'() {
    //TODO
  }

  void 'produces packer command with all required parameters including appversion and build_host for trusty'() {
    //TODO
  }

  void 'produces packer command with all required parameters including upgrade'() {
    //TODO
  }

  void 'throws exception when virtualization settings are not found for specified operating system'() {
    //TODO
  }

  void 'throws exception when virtualization settings are not found for specified region, operating system, and vm type'() {
    //TODO
  }

  void 'produce a default AWS bakeKey without base ami'() {
    //TODO
  }

  @Unroll
  void 'produce a default AWS bakeKey without base ami, even when no packages are specified'() {
    //TODO
  }

  void 'produce a default AWS bakeKey with base ami'() {
    //TODO
  }

  void 'produce a default AWS bakeKey with enhanced network enabled'() {
    //TODO
  }

  void 'produce a default AWS bakeKey with ami name'() {
    //TODO
  }

  void 'do not consider ami suffix when composing bake key'() {
    //TODO
  }

}
