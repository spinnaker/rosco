/*
 * Copyright 2020 Armory, Inc.
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

package com.netflix.spinnaker.rosco.manifests.cloudfoundry;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.CharStreams;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.rosco.jobs.JobExecutor;
import com.netflix.spinnaker.rosco.manifests.ArtifactDownloader;
import com.netflix.spinnaker.rosco.manifests.BakeManifestRequest;
import com.netflix.spinnaker.rosco.manifests.BakeManifestService;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.vault.support.JsonMapFlattener;
import org.yaml.snakeyaml.Yaml;

@Component
public class CloudFoundryBakeManifestService
    extends BakeManifestService<CloudFoundryBakeManifestRequest> {

  private static final ImmutableSet<String> supportedTemplates =
      ImmutableSet.of(BakeManifestRequest.TemplateRenderer.CF.toString());
  private final ArtifactDownloader artifactDownloader;

  @Autowired
  public CloudFoundryBakeManifestService(
      JobExecutor jobExecutor, ArtifactDownloader artifactDownloader) {
    super(jobExecutor);
    this.artifactDownloader = artifactDownloader;
  }

  @Override
  public boolean handles(String type) {
    return supportedTemplates.contains(type);
  }

  @Override
  public Artifact bake(CloudFoundryBakeManifestRequest bakeManifestRequest) throws IOException {
    Yaml yaml = new Yaml();

    String manifestTemplate =
        CharStreams.toString(
            new InputStreamReader(
                artifactDownloader.downloadArtifact(bakeManifestRequest.getManifestTemplate()),
                Charsets.UTF_8));

    Map<String, Object> vars = new HashMap<>();
    for (Artifact artifact : bakeManifestRequest.getVarsArtifacts()) {
      InputStream inputStream = artifactDownloader.downloadArtifact(artifact);
      vars.putAll(yaml.load(inputStream));
      inputStream.close();
    }
    vars = JsonMapFlattener.flatten(vars);

    for (Map.Entry<String, Object> s : vars.entrySet()) {
      manifestTemplate =
          manifestTemplate.replace("((" + s.getKey() + "))", s.getValue().toString());
    }

    return Artifact.builder()
        .type("embedded/base64")
        .name(bakeManifestRequest.getOutputArtifactName())
        .reference(Base64.getEncoder().encodeToString(manifestTemplate.getBytes()))
        .build();
  }

  @Override
  public Class<CloudFoundryBakeManifestRequest> requestType() {
    return CloudFoundryBakeManifestRequest.class;
  }
}
