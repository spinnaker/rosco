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
import static org.junit.Assert.assertTrue;
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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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

  @Test
  public void buildBakeRecipeWithGitRepoArtifact(@TempDir Path tempDir) throws IOException {
    // git/repo artifacts appear as a tarball, so create one that contains a helm chart.
    addTestHelmChart(tempDir);

    ArtifactDownloader artifactDownloader = mock(ArtifactDownloader.class);
    RoscoHelmConfigurationProperties helmConfigurationProperties =
        mock(RoscoHelmConfigurationProperties.class);
    HelmTemplateUtils helmTemplateUtils =
        new HelmTemplateUtils(artifactDownloader, helmConfigurationProperties);

    HelmBakeManifestRequest request = new HelmBakeManifestRequest();

    Artifact artifact =
        Artifact.builder().type("git/repo").reference("https://github.com/some/repo.git").build();

    // Set up the mock artifactDownloader to supply the tarball that represents
    // the git/repo artifact
    when(artifactDownloader.downloadArtifact(artifact)).thenReturn(makeTarball(tempDir));

    request.setInputArtifacts(Collections.singletonList(artifact));
    request.setNamespace("default");
    request.setOverrides(Collections.emptyMap());

    try (BakeManifestEnvironment env = BakeManifestEnvironment.create()) {
      BakeRecipe recipe = helmTemplateUtils.buildBakeRecipe(env, request);

      // Make sure we're really testing the git/repo logic
      verify(artifactDownloader).downloadArtifact(artifact);

      // Make sure the BakeManifestEnvironment has the files in our git/repo artifact.
      assertTrue(env.resolvePath("Chart.yaml").toFile().exists());
      assertTrue(env.resolvePath("values.yaml").toFile().exists());
      assertTrue(env.resolvePath("templates/foo.yaml").toFile().exists());
    }
  }

  @Test
  public void buildBakeRecipeWithGitRepoArtifactUsingHelmChartFilePath(@TempDir Path tempDir)
      throws IOException {
    // Create a tarball with a helm chart in a sub directory
    String subDirName = "subdir";
    Path subDir = tempDir.resolve(subDirName);
    addTestHelmChart(subDir);

    ArtifactDownloader artifactDownloader = mock(ArtifactDownloader.class);
    RoscoHelmConfigurationProperties helmConfigurationProperties =
        mock(RoscoHelmConfigurationProperties.class);
    HelmTemplateUtils helmTemplateUtils =
        new HelmTemplateUtils(artifactDownloader, helmConfigurationProperties);

    HelmBakeManifestRequest request = new HelmBakeManifestRequest();

    // So we know where to look for the path to the chart in the resulting helm
    // template command, specify the renderer.  Use HELM2 since the path appears
    // earlier in the argument list.
    request.setTemplateRenderer(BakeManifestRequest.TemplateRenderer.HELM2);

    // Note that supplying a location for a git/repo artifact doesn't change the
    // path in the resulting tarball.  It's here because it's likely that it's
    // used together with helmChartFilePath.  Removing it wouldn't change the
    // test.
    Artifact artifact =
        Artifact.builder()
            .type("git/repo")
            .reference("https://github.com/some/repo.git")
            .location(subDirName)
            .build();

    // Set up the mock artifactDownloader to supply the tarball that represents
    // the git/repo artifact
    when(artifactDownloader.downloadArtifact(artifact)).thenReturn(makeTarball(tempDir));

    request.setInputArtifacts(Collections.singletonList(artifact));
    request.setOverrides(Collections.emptyMap());

    // This is the key part of this test.
    request.setHelmChartFilePath(subDirName);

    try (BakeManifestEnvironment env = BakeManifestEnvironment.create()) {
      BakeRecipe recipe = helmTemplateUtils.buildBakeRecipe(env, request);

      // Make sure we're really testing the git/repo logic
      verify(artifactDownloader).downloadArtifact(artifact);

      // Make sure the BakeManifestEnvironment has the files in our git/repo
      // artifact in the expected location.
      assertTrue(env.resolvePath(Path.of(subDirName, "Chart.yaml")).toFile().exists());
      assertTrue(env.resolvePath(Path.of(subDirName, "values.yaml")).toFile().exists());
      assertTrue(env.resolvePath(Path.of(subDirName, "templates/foo.yaml")).toFile().exists());

      // And that the helm template command includes the path to the subdirectory
      //
      // Given that we're using the HELM2 renderer, the expected elements in the
      // command list are:
      //
      // 0 - the helm executable
      // 1 - template
      // 2 - the path to Chart.yaml
      assertEquals(env.resolvePath(subDirName).toString(), recipe.getCommand().get(2));
    }
  }

  /**
   * Add a helm chart for testing
   *
   * @param path the location of the helm chart (e.g. Chart.yaml)
   */
  void addTestHelmChart(Path path) throws IOException {
    addFile(
        path,
        "Chart.yaml",
        "apiVersion: v1\n"
            + "name: example\n"
            + "description: chart for testing\n"
            + "version: 0.1\n"
            + "engine: gotpl\n");

    addFile(path, "values.yaml", "foo: bar\n");

    addFile(
        path,
        "templates/foo.yaml",
        "labels:\n" + "helm.sh/chart: {{ .Chart.Name }}-{{ .Chart.Version }}\n");
  }

  /**
   * Create a new file in the temp directory
   *
   * @param path the path of the file to create (relative to the temp directory's root)
   * @param content the content of the file, or null for an empty file
   */
  void addFile(Path tempDir, String path, String content) throws IOException {
    Path pathToCreate = tempDir.resolve(path);
    pathToCreate.toFile().getParentFile().mkdirs();
    Files.write(pathToCreate, content.getBytes());
  }

  /**
   * Make a gzipped tarball of all files in a path
   *
   * @param rootPath the root path of the tarball
   * @return an InputStream containing the gzipped tarball
   */
  InputStream makeTarball(Path rootPath) throws IOException {
    ArrayList<Path> filePathsToAdd =
        Files.walk(rootPath, FileVisitOption.FOLLOW_LINKS)
            .filter(path -> !path.equals(rootPath))
            .collect(Collectors.toCollection(ArrayList::new));

    // See
    // https://commons.apache.org/proper/commons-compress/examples.html#Common_Archival_Logic
    // for background
    try (ByteArrayOutputStream os = new ByteArrayOutputStream();
        GzipCompressorOutputStream gzo = new GzipCompressorOutputStream(os);
        TarArchiveOutputStream tarArchive = new TarArchiveOutputStream(gzo)) {
      for (Path path : filePathsToAdd) {
        TarArchiveEntry tarEntry =
            new TarArchiveEntry(path.toFile(), rootPath.relativize(path).toString());
        tarArchive.putArchiveEntry(tarEntry);
        if (path.toFile().isFile()) {
          IOUtils.copy(Files.newInputStream(path), tarArchive);
        }
        tarArchive.closeArchiveEntry();
      }

      tarArchive.finish();
      gzo.finish();

      return new ByteArrayInputStream(os.toByteArray());
    }
  }
}
