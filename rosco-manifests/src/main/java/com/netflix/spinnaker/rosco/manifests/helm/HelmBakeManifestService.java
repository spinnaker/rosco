package com.netflix.spinnaker.rosco.manifests.helm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.rosco.api.BakeStatus;
import com.netflix.spinnaker.rosco.jobs.BakeRecipe;
import com.netflix.spinnaker.rosco.jobs.JobExecutor;
import com.netflix.spinnaker.rosco.jobs.JobRequest;
import com.netflix.spinnaker.rosco.manifests.BakeManifestService;
import com.netflix.spinnaker.rosco.manifests.TemplateUtils.BakeManifestEnvironment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@Component
public class HelmBakeManifestService implements BakeManifestService {
  @Autowired
  HelmTemplateUtils helmTemplateUtils;

  @Autowired
  JobExecutor jobExecutor;


  @Override
  public boolean handles(String type) {
    return type.equals("helm");
  }

  public Artifact bake(Map<String, Object> request) {
    ObjectMapper mapper = new ObjectMapper();
    HelmBakeManifestRequest bakeManifestRequest = mapper.convertValue(request, HelmBakeManifestRequest.class);
    BakeManifestEnvironment env = new BakeManifestEnvironment();
    BakeRecipe recipe = helmTemplateUtils.buildBakeRecipe(env, bakeManifestRequest);

    BakeStatus bakeStatus;

    try {
      JobRequest jobRequest = new JobRequest(recipe.getCommand(), new ArrayList<>(), UUID.randomUUID().toString());
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
        throw new IllegalStateException("Bake of " + request + " failed: " + bakeStatus.getLogsContent());
      }
    } finally {
      env.cleanup();
    }

    return Artifact.builder()
        .type("embedded/base64")
        .name(bakeManifestRequest.getOutputArtifactName())
        .reference(Base64.getEncoder().encodeToString(bakeStatus.getLogsContent().getBytes()))
        .build();
  }
}
