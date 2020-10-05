/*
 * Copyright 2019 Google, LLC
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

package com.netflix.spinnaker.rosco.manifests.helm;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.rosco.jobs.BakeRecipe;
import com.netflix.spinnaker.rosco.manifests.ArtifactDownloader;
import com.netflix.spinnaker.rosco.manifests.BakeManifestEnvironment;
import com.netflix.spinnaker.rosco.manifests.BakeManifestRequest;
import com.netflix.spinnaker.rosco.manifests.config.RoscoHelmConfigurationProperties;
import java.io.IOException;
import java.util.Collections;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

@RunWith(JUnitPlatform.class)
final class HelmTemplateUtilsTest {
  @Test
  public void nullReferenceTest() throws IOException {
    ArtifactDownloader artifactDownloader = mock(ArtifactDownloader.class);
    RoscoHelmConfigurationProperties helmConfigurationProperties =
        mock(RoscoHelmConfigurationProperties.class);
    HelmTemplateUtils helmTemplateUtils =
        new HelmTemplateUtils(artifactDownloader, helmConfigurationProperties);
    Artifact chartArtifact = Artifact.builder().name("test-artifact").version("3").build();

    HelmBakeManifestRequest bakeManifestRequest = new HelmBakeManifestRequest();
    bakeManifestRequest.setInputArtifacts(ImmutableList.of(chartArtifact));
    bakeManifestRequest.setOverrides(ImmutableMap.of());

    try (BakeManifestEnvironment env = BakeManifestEnvironment.create()) {
      BakeRecipe recipe = helmTemplateUtils.buildBakeRecipe(env, bakeManifestRequest);
    }
  }

  @Test
  public void removeTestsDirectoryTemplatesWithTests() throws IOException {
    String inputManifests =
        "---\n"
            + "# Source: mysql/templates/pvc.yaml\n"
            + "\n"
            + "kind: PersistentVolumeClaim\n"
            + "apiVersion: v1\n"
            + "metadata:\n"
            + "  name: release-name-mysql\n"
            + "  namespace: default\n"
            + "spec:\n"
            + "  accessModes:\n"
            + "    - \"ReadWriteOnce\"\n"
            + "  resources:\n"
            + "    requests:\n"
            + "      storage: \"8Gi\"\n"
            + "---\n"
            + "# Source: mysql/templates/tests/test-configmap.yaml\n"
            + "apiVersion: v1\n"
            + "kind: ConfigMap\n"
            + "metadata:\n"
            + "  name: release-name-mysql-test\n"
            + "  namespace: default\n"
            + "data:\n"
            + "  run.sh: |-\n";

    ArtifactDownloader artifactDownloader = mock(ArtifactDownloader.class);
    RoscoHelmConfigurationProperties helmConfigurationProperties =
        mock(RoscoHelmConfigurationProperties.class);
    HelmTemplateUtils helmTemplateUtils =
        new HelmTemplateUtils(artifactDownloader, helmConfigurationProperties);

    String output = helmTemplateUtils.removeTestsDirectoryTemplates(inputManifests);

    String expected =
        "---\n"
            + "# Source: mysql/templates/pvc.yaml\n"
            + "\n"
            + "kind: PersistentVolumeClaim\n"
            + "apiVersion: v1\n"
            + "metadata:\n"
            + "  name: release-name-mysql\n"
            + "  namespace: default\n"
            + "spec:\n"
            + "  accessModes:\n"
            + "    - \"ReadWriteOnce\"\n"
            + "  resources:\n"
            + "    requests:\n"
            + "      storage: \"8Gi\"\n";

    assertEquals(expected.trim(), output.trim());
  }

  @Test
  public void removeTestsDirectoryTemplatesWithoutTests() throws IOException {
    String inputManifests =
        "---\n"
            + "# Source: mysql/templates/pvc.yaml\n"
            + "\n"
            + "kind: PersistentVolumeClaim\n"
            + "apiVersion: v1\n"
            + "metadata:\n"
            + "  name: release-name-mysql\n"
            + "  namespace: default\n"
            + "spec:\n"
            + "  accessModes:\n"
            + "    - \"ReadWriteOnce\"\n"
            + "  resources:\n"
            + "    requests:\n"
            + "      storage: \"8Gi\"\n"
            + "---\n"
            + "# Source: mysql/templates/configmap.yaml\n"
            + "apiVersion: v1\n"
            + "kind: ConfigMap\n"
            + "metadata:\n"
            + "  name: release-name-mysql-test\n"
            + "  namespace: default\n"
            + "data:\n"
            + "  run.sh: |-\n";

    ArtifactDownloader artifactDownloader = mock(ArtifactDownloader.class);
    RoscoHelmConfigurationProperties helmConfigurationProperties =
        mock(RoscoHelmConfigurationProperties.class);
    HelmTemplateUtils helmTemplateUtils =
        new HelmTemplateUtils(artifactDownloader, helmConfigurationProperties);

    String output = helmTemplateUtils.removeTestsDirectoryTemplates(inputManifests);

    assertEquals(inputManifests.trim(), output.trim());
  }

  @ParameterizedTest
  @MethodSource("helmRendererArgs")
  public void buildBakeRecipeSelectsHelmExecutableByVersion(
      String command, BakeManifestRequest.TemplateRenderer templateRenderer) throws IOException {
    ArtifactDownloader artifactDownloader = mock(ArtifactDownloader.class);
    RoscoHelmConfigurationProperties helmConfigurationProperties =
        mock(RoscoHelmConfigurationProperties.class);
    HelmTemplateUtils helmTemplateUtils =
        new HelmTemplateUtils(artifactDownloader, helmConfigurationProperties);

    HelmBakeManifestRequest request = new HelmBakeManifestRequest();
    Artifact artifact = Artifact.builder().build();
    request.setInputArtifacts(Collections.singletonList(artifact));
    request.setNamespace("default");
    request.setOverrides(Collections.emptyMap());

    // The mock return values must equal command for the assert below to work
    when(helmConfigurationProperties.getV2ExecutablePath()).thenReturn("helm2");
    when(helmConfigurationProperties.getV3ExecutablePath()).thenReturn("helm3");

    request.setTemplateRenderer(templateRenderer);
    try (BakeManifestEnvironment env = BakeManifestEnvironment.create()) {
      BakeRecipe recipe = helmTemplateUtils.buildBakeRecipe(env, request);

      verify(helmConfigurationProperties, atMost(1)).getV2ExecutablePath();
      verify(helmConfigurationProperties, atMost(1)).getV3ExecutablePath();
      verifyNoMoreInteractions(helmConfigurationProperties);

      assertEquals(command, recipe.getCommand().get(0));
    }
  }

  private static Stream<Arguments> helmRendererArgs() {
    return Stream.of(
        Arguments.of("helm2", BakeManifestRequest.TemplateRenderer.HELM2),
        Arguments.of("helm3", BakeManifestRequest.TemplateRenderer.HELM3));
  }
}
