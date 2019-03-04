/*
 * Copyright 2019 Armory
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.rosco.manifests.jinja;

import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.rosco.jobs.BakeRecipe;
import com.netflix.spinnaker.rosco.manifests.TemplateUtils;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class JinjaTemplateUtils extends TemplateUtils {
  public BakeRecipe buildBakeRecipe(BakeManifestEnvironment env, JinjaBakeManifestRequest request) {
    BakeRecipe result = new BakeRecipe();
    result.setName(request.getOutputName());

    Path templatePath;
    List<Path> valuePaths = new ArrayList<>();
    List<Artifact> inputArtifacts = request.getInputArtifacts();

    if (inputArtifacts == null || inputArtifacts.isEmpty()) {
      throw new IllegalArgumentException("At least one input artifact must be provided to bake.");
    }

    try {
      templatePath = downloadArtifactToTmpFile(env, inputArtifacts.get(0));

      for (Artifact valueArtifact : inputArtifacts.subList(1, inputArtifacts.size())) {
        valuePaths.add(downloadArtifactToTmpFile(env, valueArtifact));
      }
    } catch (IOException e) {
      throw new IllegalStateException("Failed to fetch Jinja template: " + e.getMessage(), e);
    }


    List<String> command = new ArrayList<>();
    command.add("jinja2");
    command.add(templatePath.toString());
    for (Path valuePath : valuePaths) {
      command.add(valuePath.toString());
    }

    Map<String, Object> overrides = request.getOverrides();
    if (overrides != null && !overrides.isEmpty()) {
      List<String> overrideList = new ArrayList<>();
      for (Map.Entry<String, Object> entry : overrides.entrySet()) {
        command.add("-D");
        command.add(entry.getKey() + "=" + entry.getValue().toString());
      }
    }

    command.add("--format");
    command.add(request.getInputFormat());

    result.setCommand(command);
    return result;
  }
}
