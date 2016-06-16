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

import com.netflix.spinnaker.rosco.api.BakeRequest
import groovy.transform.EqualsAndHashCode
import org.springframework.util.StringUtils

class PackageNameConverter {

  @EqualsAndHashCode
  static class OsPackageName {
    String name
    String version
    String release
    String arch
  }

  // Naming-convention for debs is name_version-release_arch.
  // For example: nflx-djangobase-enhanced_0.1-h12.170cdbd_all
  public static OsPackageName parseDebPackageName(String fullyQualifiedPackageName) {
    OsPackageName osPackageName = new OsPackageName()

    osPackageName.with {
      List<String> parts = fullyQualifiedPackageName?.tokenize("_")

      if (parts) {
        name = parts[0]

        if (parts.size > 1) {
          List<String> versionReleaseParts = parts[1].tokenize("-")

          if (versionReleaseParts) {
            version = versionReleaseParts[0]

            if (versionReleaseParts.size > 1) {
              release = versionReleaseParts[1]
            }
          }

          if (parts.size > 2) {
            arch = parts[2]
          }
        }
      }
    }

    osPackageName
  }

  // Naming-convention for rpms is name-version-release.arch.
  // For example: nflx-djangobase-enhanced-0.1-h12.170cdbd.all
  public static OsPackageName parseRpmPackageName(String fullyQualifiedPackageName) {
    OsPackageName osPackageName = new OsPackageName()

    osPackageName.with {
      if (fullyQualifiedPackageName) {
        int startOfArch = fullyQualifiedPackageName.lastIndexOf('.')

        if (startOfArch != -1) {
          arch = fullyQualifiedPackageName.substring(startOfArch + 1)
          fullyQualifiedPackageName = fullyQualifiedPackageName.substring(0, startOfArch)
        }

        List<String> parts = fullyQualifiedPackageName.tokenize("-")

        if (parts.size >= 3) {
          release = parts.pop()
          version = parts.pop()
          name = parts.join("-")
        }
      }
    }

    osPackageName
  }

  public static OsPackageName buildOsPackageName(BakeRequest.PackageType packageType, String packageName) {
    switch(packageType) {
      case BakeRequest.PackageType.DEB:
        return PackageNameConverter.parseDebPackageName(packageName)
      case BakeRequest.PackageType.RPM:
        return PackageNameConverter.parseRpmPackageName(packageName)
      default:
        throw new IllegalArgumentException("Unrecognized packageType '$packageType'.")
    }
  }

  public static String buildAppVersionStr(BakeRequest bakeRequest, OsPackageName osPackageName) {
    // As per source of AppVersion, these are valid appversion tags:
    //   subscriberha-1.0.0-h150
    //   subscriberha-1.0.0-h150.586499
    //   subscriberha-1.0.0-h150.586499/WE-WAPP-subscriberha/150
    String appVersion = osPackageName.name

    osPackageName.with {
      if (version) {
        appVersion += "-$version"

        if (bakeRequest.build_number) {
          appVersion += "-h$bakeRequest.build_number"

          if (bakeRequest.commit_hash) {
            appVersion += ".$bakeRequest.commit_hash"
          }

          if (bakeRequest.job) {
            // Travis job name and Jenkins job name with folder may contain slashes in the job name
            // that make AppVersion.parseName fail. Replace all slashes in the job name with hyphens.
            def job = bakeRequest.job.replaceAll("/", "-")
            appVersion += "/$job/$bakeRequest.build_number"
          }
        }
      }
    }

    appVersion
  }

}
