package com.netflix.spinnaker.rosco.manifests.helmfile;

import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.rosco.manifests.BakeManifestRequest;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class HelmfileBakeManifestRequest extends BakeManifestRequest {
  private String helmfileFilePath;
  private String environment;
  private String namespace;

  List<Artifact> inputArtifacts;
  boolean includeCRDs;
}
