package com.netflix.spinnaker.rosco.manifests.helm;

import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.rosco.api.BakeStatus;
import com.netflix.spinnaker.rosco.jobs.BakeRecipe;
import com.netflix.spinnaker.rosco.jobs.JobExecutor;
import com.netflix.spinnaker.rosco.jobs.JobRequest;
import com.netflix.spinnaker.rosco.manifests.BakeManifestService;
import com.netflix.spinnaker.rosco.manifests.TemplateUtils.BakeManifestEnvironment;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class HelmBakeManifestService implements BakeManifestService<HelmBakeManifestRequest> {
  private final HelmTemplateUtils helmTemplateUtils;
  private final JobExecutor jobExecutor;
  private static final String HELM_TYPE = "HELM2";

  public HelmBakeManifestService(HelmTemplateUtils helmTemplateUtils, JobExecutor jobExecutor) {
    this.helmTemplateUtils = helmTemplateUtils;
    this.jobExecutor = jobExecutor;
  }

  @Override
  public Class<HelmBakeManifestRequest> requestType() {
    return HelmBakeManifestRequest.class;
  }

  @Override
  public boolean handles(String type) {
    return type.toUpperCase().equals(HELM_TYPE);
  }

  public Artifact bake(HelmBakeManifestRequest bakeManifestRequest) {
    BakeManifestEnvironment env = new BakeManifestEnvironment();
    BakeRecipe recipe = helmTemplateUtils.buildBakeRecipe(env, bakeManifestRequest);
    BakeStatus bakeStatus;

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
        }

        bakeStatus = jobExecutor.updateJob(jobId);
      }

      if (bakeStatus.getResult() != BakeStatus.Result.SUCCESS) {
        throw new IllegalStateException(
            "Bake of " + bakeManifestRequest + " failed: " + bakeStatus.getLogsContent());
      }
    } finally {
      env.cleanup();
    }

    return Artifact.builder()
        .type("embedded/base64")
        .name(bakeManifestRequest.getOutputArtifactName())
        .reference(Base64.getEncoder().encodeToString(bakeStatus.getOutputContent().getBytes()))
        .build();
  }
}
