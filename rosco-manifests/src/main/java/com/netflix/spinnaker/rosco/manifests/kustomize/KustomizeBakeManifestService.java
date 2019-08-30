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
import com.netflix.spinnaker.rosco.api.BakeStatus;
import com.netflix.spinnaker.rosco.jobs.BakeRecipe;
import com.netflix.spinnaker.rosco.jobs.JobExecutor;
import com.netflix.spinnaker.rosco.jobs.JobRequest;
import com.netflix.spinnaker.rosco.manifests.BakeManifestException;
import com.netflix.spinnaker.rosco.manifests.BakeManifestService;
import com.netflix.spinnaker.rosco.manifests.TemplateUtils.BakeManifestEnvironment;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class KustomizeBakeManifestService
    implements BakeManifestService<KustomizeBakeManifestRequest> {
  private final KustomizeTemplateUtils kustomizeTemplateUtils;
  private final JobExecutor jobExecutor;

  private static final String KUSTOMIZE = "KUSTOMIZE";

  public KustomizeBakeManifestService(
      KustomizeTemplateUtils kustomizeTemplateUtils, JobExecutor jobExecutor) {
    this.kustomizeTemplateUtils = kustomizeTemplateUtils;
    this.jobExecutor = jobExecutor;
  }

  @Override
  public Class<KustomizeBakeManifestRequest> requestType() {
    return KustomizeBakeManifestRequest.class;
  }

  @Override
  public boolean handles(String type) {
    return type.toUpperCase().equals(KUSTOMIZE);
  }

  public Artifact bake(KustomizeBakeManifestRequest kustomizeBakeManifestRequest)
      throws BakeManifestException {
    BakeManifestEnvironment env = new BakeManifestEnvironment();
    BakeRecipe recipe = kustomizeTemplateUtils.buildBakeRecipe(env, kustomizeBakeManifestRequest);
    BakeStatus bakeStatus = null;
    try {
      JobRequest jobRequest =
          new JobRequest(
              recipe.getCommand(),
              new ArrayList<>(),
              UUID.randomUUID().toString(),
              AuthenticatedRequest.getSpinnakerExecutionId().orElse(null),
              false);
      String jobId = jobExecutor.startJob(jobRequest);
      bakeStatus = jobExecutor.updateJob(jobId);
      while (bakeStatus == null || bakeStatus.getState() == BakeStatus.State.RUNNING) {
        try {
          Thread.sleep(1000);
        } catch (InterruptedException ignored) {
          jobExecutor.cancelJob(jobId);
          Thread.currentThread().interrupt();
          throw new BakeManifestException("Kustomize bake was interrupted.");
        }
        bakeStatus = jobExecutor.updateJob(jobId);
      }
      if (bakeStatus.getResult() != BakeStatus.Result.SUCCESS) {
        throw new IllegalStateException(
            "Bake of " + kustomizeBakeManifestRequest + " failed: " + bakeStatus.getLogsContent());
      }
      return Artifact.builder()
          .type("embedded/base64")
          .name(kustomizeBakeManifestRequest.getOutputArtifactName())
          .reference(Base64.getEncoder().encodeToString(bakeStatus.getOutputContent().getBytes()))
          .build();
    } finally {
      env.cleanup();
    }
  }
}
