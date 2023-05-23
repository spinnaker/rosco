package com.netflix.spinnaker.rosco.manifests.helmfile;

import static com.netflix.spinnaker.rosco.manifests.BakeManifestRequest.TemplateRenderer;

import com.google.common.collect.ImmutableSet;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.rosco.jobs.BakeRecipe;
import com.netflix.spinnaker.rosco.jobs.JobExecutor;
import com.netflix.spinnaker.rosco.manifests.BakeManifestEnvironment;
import com.netflix.spinnaker.rosco.manifests.BakeManifestService;
import java.io.IOException;
import java.util.Base64;
import org.springframework.stereotype.Component;

@Component
public class HelmfileBakeManifestService extends BakeManifestService<HelmfileBakeManifestRequest> {
  private final HelmfileTemplateUtils helmfileTemplateUtils;
  private static final ImmutableSet<String> supportedTemplates =
      ImmutableSet.of(TemplateRenderer.HELMFILE.toString());

  public HelmfileBakeManifestService(
      HelmfileTemplateUtils helmTemplateUtils, JobExecutor jobExecutor) {
    super(jobExecutor);
    this.helmfileTemplateUtils = helmTemplateUtils;
  }

  @Override
  public Class<HelmfileBakeManifestRequest> requestType() {
    return HelmfileBakeManifestRequest.class;
  }

  @Override
  public boolean handles(String type) {
    return supportedTemplates.contains(type);
  }

  public Artifact bake(HelmfileBakeManifestRequest helmfileBakeManifestRequest) throws IOException {
    try (BakeManifestEnvironment env = BakeManifestEnvironment.create()) {
      BakeRecipe recipe = helmfileTemplateUtils.buildBakeRecipe(env, helmfileBakeManifestRequest);

      String bakeResult = helmfileTemplateUtils.removeTestsDirectoryTemplates(doBake(recipe));
      return Artifact.builder()
          .type("embedded/base64")
          .name(helmfileBakeManifestRequest.getOutputArtifactName())
          .reference(Base64.getEncoder().encodeToString(bakeResult.getBytes()))
          .build();
    }
  }
}
