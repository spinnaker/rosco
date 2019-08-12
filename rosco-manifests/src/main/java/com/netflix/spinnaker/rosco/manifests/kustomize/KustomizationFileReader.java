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

import com.google.common.collect.ImmutableList;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.rosco.manifests.kustomize.mapping.Kustomization;
import com.netflix.spinnaker.rosco.services.ClouddriverService;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import retrofit.RetrofitError;
import retrofit.client.Response;

@Component
@Slf4j
public class KustomizationFileReader {
  private final ClouddriverService clouddriverService;
  private final RetrySupport retrySupport = new RetrySupport();
  private static final List<String> KUSTOMIZATION_FILENAMES =
      ImmutableList.of("kustomization.yaml", "kustomization.yml", "kustomization");

  public KustomizationFileReader(ClouddriverService clouddriverService) {
    this.clouddriverService = clouddriverService;
  }

  public Kustomization getKustomization(Artifact artifact, String possibleName) throws IOException {
    Path artifactPath = Paths.get(artifact.getReference());
    // sort list of names, trying the possibleName first.
    List<String> names =
        KUSTOMIZATION_FILENAMES.stream()
            .sorted(
                (a, b) ->
                    a.equals(possibleName) ? -1 : (b.equals(possibleName) ? 1 : a.compareTo(b)))
            .collect(Collectors.toList());

    for (String name : names) {
      try {
        Artifact testArtifact = artifactFromBase(artifact, artifactPath.resolve(name).toString());
        Kustomization kustomization = convert(testArtifact);
        kustomization.setKustomizationFilename(name);
        return kustomization;
      } catch (RetrofitError | IOException e) {
        log.error("Unable to convert kustomization file to Object: " + name);
      }
    }

    return null;
  }

  private Artifact artifactFromBase(Artifact artifact, String path) {
    return Artifact.builder()
        .reference(path)
        .artifactAccount(artifact.getArtifactAccount())
        .type(artifact.getType())
        .build();
  }

  private Kustomization convert(Artifact artifact) throws IOException {
    // TODO(ethanfrogers): figure out how to use safe constructor here.
    Constructor constructor = new Constructor(Kustomization.class);
    Yaml yaml = new Yaml(constructor);
    InputStream contents = downloadFile(artifact);
    return yaml.loadAs(contents, Kustomization.class);
  }

  private InputStream downloadFile(Artifact artifact) throws IOException {
    Response response =
        retrySupport.retry(() -> clouddriverService.fetchArtifact(artifact), 5, 1000, true);
    return response.getBody().in();
  }
}
