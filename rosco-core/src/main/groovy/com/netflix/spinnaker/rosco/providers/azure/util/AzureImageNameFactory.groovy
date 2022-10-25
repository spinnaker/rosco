/*
 * Copyright 2022 Armory.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.rosco.providers.azure.util

import com.netflix.spinnaker.rosco.api.BakeRequest
import com.netflix.spinnaker.rosco.providers.util.ImageNameFactory
import com.netflix.spinnaker.rosco.providers.util.PackageNameConverter

class AzureImageNameFactory extends ImageNameFactory {

    @Override
    def buildImageName(BakeRequest bakeRequest, List<PackageNameConverter.OsPackageName> osPackageNames) {
        String timestamp = clock.millis()
        String baseImageName = osPackageNames ? osPackageNames.first()?.name : ""
        String baseImageArch = osPackageNames ? osPackageNames.first()?.arch : "all"

        String baseName = bakeRequest.base_name ?: baseImageName
        String arch = baseImageArch ?: "all"
        String release = bakeRequest.base_label ?: timestamp
        String os = bakeRequest.base_os ?: bakeRequest.os_type

        return [baseName, arch, release, os].findAll{it}.join("-")
    }
}
