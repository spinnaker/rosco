package com.netflix.spinnaker.rosco.manifests.helm;

import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.rosco.manifests.BakeManifestRequest;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class HelmBakeManifestRequest extends BakeManifestRequest {
  String namespace;

  /**
   * The 0th element is (or contains) the template/helm chart. The rest (possibly none) are values
   * files.
   */
  List<Artifact> inputArtifacts;

  boolean rawOverrides;
}
