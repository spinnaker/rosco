/*
 * Copyright 2019 Armory, Inc.
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

package com.netflix.spinnaker.rosco.manifests.kustomize;

import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.rosco.jobs.BakeRecipe;
import com.netflix.spinnaker.rosco.manifests.TemplateUtils;
import com.netflix.spinnaker.rosco.manifests.kustomize.mapping.Kustomization;
import com.netflix.spinnaker.rosco.services.ClouddriverService;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class KustomizeTemplateUtils extends TemplateUtils {
  private final KustomizationFileReader kustomizationFileReader;

  public KustomizeTemplateUtils(
      KustomizationFileReader kustomizationFileReader, ClouddriverService clouddriverService) {
    super(clouddriverService);
    this.kustomizationFileReader = kustomizationFileReader;
  }

  public BakeRecipe buildBakeRecipe(
      BakeManifestEnvironment env, KustomizeBakeManifestRequest request) {
    BakeRecipe result = new BakeRecipe();
    result.setName(request.getOutputName());
    Artifact artifact = request.getInputArtifact();
    if (artifact == null) {
      throw new IllegalArgumentException("Exactly one input artifact must be provided to bake.");
    }
    String kustomizationfilename = FilenameUtils.getName(artifact.getReference());
    if (kustomizationfilename != null
        && !kustomizationfilename.toUpperCase().contains("KUSTOMIZATION")) {
      throw new IllegalArgumentException("The inputArtifact should be a valid kustomization file.");
    }
    String referenceBaseURL = artifact.getReference().replace(artifact.getName(), "");
    String templatePath =
        Paths.get(
                env.getStagingPath()
                    .toString()
                    .concat(Paths.get(artifact.getName()).getParent().toString()))
            .toString();

    try {
      List<Artifact> artifacts = getArtifacts(artifact);
      for (Artifact ar : artifacts) {
        downloadArtifactToTmpFileStructure(env, ar, referenceBaseURL);
      }
    } catch (IOException e) {
      throw new IllegalStateException("Failed to fetch kustomize files: " + e.getMessage(), e);
    }

    List<String> command = new ArrayList<>();
    command.add("kustomize");
    command.add("build");
    command.add(templatePath);
    result.setCommand(command);

    return result;
  }

  private List<Artifact> getArtifacts(Artifact artifact) {
    try {
      Set<String> files = getFilesFromArtifact(artifact);
      List<Artifact> artifacts =
          files.stream()
              .map(
                  f -> {
                    return Artifact.builder()
                        .reference(f)
                        .artifactAccount(artifact.getArtifactAccount())
                        .customKind(artifact.isCustomKind())
                        .location(artifact.getLocation())
                        .metadata(artifact.getMetadata())
                        .name(artifact.getName())
                        .provenance(artifact.getProvenance())
                        .type(artifact.getType())
                        .version(artifact.getVersion())
                        .build();
                  })
              .collect(Collectors.toList());
      return artifacts;
    } catch (IOException e) {
      throw new IllegalStateException("Error setting references in artifacts " + e.getMessage(), e);
    }
  }

  private HashSet<String> getFilesFromArtifact(Artifact artifact) throws IOException {
    String referenceBaseURL = artifact.getReference().replace(artifact.getName(), "");
    String filename = FilenameUtils.getName(artifact.getName());
    Path base = Paths.get(artifact.getName()).getParent();
    artifact.setName(base.toString());
    return getFilesFromArtifact(artifact, referenceBaseURL, base, filename);
  }

  private HashSet<String> getFilesFromArtifact(
      Artifact artifact, String referenceBaseURL, Path base, String filename) throws IOException {
    HashSet<String> filesToDownload = new HashSet<>();
    Path artifactPath = Paths.get(referenceBaseURL.concat(base.toString()));
    artifact.setReference(artifactPath.toString());
    Kustomization kustomization = kustomizationFileReader.getKustomization(artifact, filename);
    filesToDownload.addAll(
        kustomization.getFilesToDownload(referenceBaseURL.concat(base.toString())));
    if (kustomization.getFilesToEvaluate() != null
        && !kustomization.getFilesToEvaluate().isEmpty()) {
      for (String evaluate : kustomization.getFilesToEvaluate()) {
        boolean isFolder = false;
        if (evaluate.contains(".")) {
          String tmp = evaluate.substring(evaluate.lastIndexOf(".") + 1);
          if (!tmp.contains("/")) {
            filesToDownload.add(
                referenceBaseURL.concat(base.toString()).concat(File.separator).concat(evaluate));
          } else {
            isFolder = true;
          }
        } else {
          isFolder = true;
        }
        if (isFolder) {
          Path tmpBase = Paths.get(FilenameUtils.normalize(base.resolve(evaluate).toString()));
          artifact.setName(tmpBase.toString());
          filesToDownload.addAll(
              getFilesFromArtifact(artifact, referenceBaseURL, tmpBase, filename));
        }
      }
    }
    return filesToDownload;
  }
}
