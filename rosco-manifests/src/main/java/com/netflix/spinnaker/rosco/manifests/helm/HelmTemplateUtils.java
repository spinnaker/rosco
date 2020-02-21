package com.netflix.spinnaker.rosco.manifests.helm;

import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.rosco.jobs.BakeRecipe;
import com.netflix.spinnaker.rosco.manifests.ArtifactDownloader;
import com.netflix.spinnaker.rosco.manifests.BakeManifestEnvironment;
import com.netflix.spinnaker.rosco.manifests.BakeManifestRequest;
import com.netflix.spinnaker.rosco.manifests.config.RoscoHelmConfigurationProperties;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class HelmTemplateUtils {
  private static final String MANIFEST_SEPARATOR = "---\n";
  private static final Pattern REGEX_TESTS_MANIFESTS =
      Pattern.compile("# Source: .*/templates/tests/.*");

  private final ArtifactDownloader artifactDownloader;
  private final RoscoHelmConfigurationProperties helmConfigurationProperties;

  public HelmTemplateUtils(
      ArtifactDownloader artifactDownloader,
      RoscoHelmConfigurationProperties helmConfigurationProperties) {
    this.artifactDownloader = artifactDownloader;
    this.helmConfigurationProperties = helmConfigurationProperties;
  }

  public BakeRecipe buildBakeRecipe(BakeManifestEnvironment env, HelmBakeManifestRequest request) {
    BakeRecipe result = new BakeRecipe();
    result.setName(request.getOutputName());

    Path templatePath;
    List<Path> valuePaths = new ArrayList<>();
    List<Artifact> inputArtifacts = request.getInputArtifacts();
    if (inputArtifacts == null || inputArtifacts.isEmpty()) {
      throw new IllegalArgumentException("At least one input artifact must be provided to bake");
    }

    try {
      templatePath = downloadArtifactToTmpFile(env, inputArtifacts.get(0));

      // not a stream to keep exception handling cleaner
      for (Artifact valueArtifact : inputArtifacts.subList(1, inputArtifacts.size())) {
        valuePaths.add(downloadArtifactToTmpFile(env, valueArtifact));
      }
    } catch (IOException e) {
      throw new IllegalStateException("Failed to fetch helm template: " + e.getMessage(), e);
    }

    List<String> command = new ArrayList<>();
    String executable = getHelmExecutableForRequest(request);

    // Helm `template` subcommands are slightly different
    // helm 2: helm template <chart> --name <release name>
    // helm 3: helm template <release name> <chart>
    // Other parameters such as --namespace, --set, and --values are the same
    command.add(executable);
    command.add("template");
    if (HelmBakeManifestRequest.TemplateRenderer.HELM2.equals(request.getTemplateRenderer())) {
      command.add(templatePath.toString());
      command.add("--name");
      command.add(request.getOutputName());
    } else {
      command.add(request.getOutputName());
      command.add(templatePath.toString());
    }

    String namespace = request.getNamespace();
    if (namespace != null && !namespace.isEmpty()) {
      command.add("--namespace");
      command.add(namespace);
    }

    Map<String, Object> overrides = request.getOverrides();
    if (!overrides.isEmpty()) {
      List<String> overrideList = new ArrayList<>();
      for (Map.Entry<String, Object> entry : overrides.entrySet()) {
        overrideList.add(entry.getKey() + "=" + entry.getValue().toString());
      }
      String overrideOption = request.isRawOverrides() ? "--set" : "--set-string";
      command.add(overrideOption);
      command.add(String.join(",", overrideList));
    }

    if (!valuePaths.isEmpty()) {
      command.add("--values");
      command.add(valuePaths.stream().map(Path::toString).collect(Collectors.joining(",")));
    }

    result.setCommand(command);

    return result;
  }

  public String removeTestsDirectoryTemplates(String inputString) {
    return Arrays.stream(inputString.split(MANIFEST_SEPARATOR))
        .filter(manifest -> !REGEX_TESTS_MANIFESTS.matcher(manifest).find())
        .collect(Collectors.joining(MANIFEST_SEPARATOR));
  }

  private Path downloadArtifactToTmpFile(BakeManifestEnvironment env, Artifact artifact)
      throws IOException {
    String fileName = UUID.randomUUID().toString();
    Path targetPath = env.resolvePath(fileName);
    artifactDownloader.downloadArtifactToFile(artifact, targetPath);
    return targetPath;
  }

  private String getHelmExecutableForRequest(HelmBakeManifestRequest request) {
    if (BakeManifestRequest.TemplateRenderer.HELM2.equals(request.getTemplateRenderer())) {
      return helmConfigurationProperties.getV2ExecutablePath();
    }
    return helmConfigurationProperties.getV3ExecutablePath();
  }
}
