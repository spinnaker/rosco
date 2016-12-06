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

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import io.swagger.annotations.ApiModelProperty

/**
 * The details of a completed bake.
 *
 * @see BakeryController#lookupBake
 */
@CompileStatic
@EqualsAndHashCode(includes = "id")
@ToString(includeNames = true)
class Bake {
  @ApiModelProperty(value="The id of the bake job.")
  String id
  @Deprecated
  String ami // Replaced by Artifact.name
  @Deprecated
  String image_name // Replaced by Artifact.reference
  Artifact artifact // The artifact produced by the bake
  BuildInfo build_info // The build responsible for the content of this bake
}
