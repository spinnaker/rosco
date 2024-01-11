/*
 * Copyright 2024 Salesforce, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.rosco.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.rosco.Main;
import com.netflix.spinnaker.rosco.executor.BakePoller;
import com.netflix.spinnaker.rosco.manifests.ArtifactDownloader;
import com.netflix.spinnaker.rosco.manifests.BakeManifestEnvironment;
import com.netflix.spinnaker.rosco.manifests.BakeManifestRequest.TemplateRenderer;
import com.netflix.spinnaker.rosco.manifests.config.RoscoHelmConfigurationProperties;
import com.netflix.spinnaker.rosco.manifests.helm.HelmBakeManifestRequest;
import com.netflix.spinnaker.rosco.manifests.helm.HelmTemplateUtils;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.context.WebApplicationContext;

/**
 * This class tests the behavior of Helm chart rendering. It focuses on verifying the priority and
 * effectiveness of various Helm value overrides during the baking process of a manifest. The class
 * covers a range of scenarios including default values, overrides through '--values' and
 * '--set-string' flags, and the use of external values files. It also tests different Helm versions
 * to ensure compatibility and correctness across versions.
 */
@SpringBootTest(classes = Main.class)
@TestPropertySource(properties = "spring.application.name = rosco")
class V2BakeryControllerWithArtifactDownloaderTest {

  public static final String FOO_VALUE_DEFAULT = "bar_default";
  public static final String FOO_VALUE_OVERRIDES = "bar_overrides";
  public static final String FOO_VALUE_EXTERNAL = "bar_external";
  public static final String EMPTY_OVERRIDES = "";
  public static final String TEMPLATE_OUTPUT_FORMAT =
      "---\n"
          + "# Source: example/templates/foo.yaml\n"
          + "labels:\n"
          + "helm.sh/chart: example-0.1\n"
          + "foo: %s\n";
  private MockMvc webAppMockMvc;
  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired ObjectMapper objectMapper;
  private HelmBakeManifestRequest bakeManifestRequest;
  @MockBean private ArtifactDownloader artifactDownloader;
  @Autowired private RoscoHelmConfigurationProperties roscoHelmConfigurationProperties;
  @SpyBean private HelmTemplateUtils helmTemplateUtils;
  @MockBean BakePoller bakePoller;

  @BeforeEach
  void init(TestInfo testInfo) {
    System.out.println("--------------- Test " + testInfo.getTestMethod());

    webAppMockMvc = webAppContextSetup(webApplicationContext).build();
    bakeManifestRequest = new HelmBakeManifestRequest();
    bakeManifestRequest.setOutputName("test");
    bakeManifestRequest.setOutputArtifactName("test_artifact");
    Artifact artifact = Artifact.builder().type("git/repo").build();
    bakeManifestRequest.setInputArtifacts(ImmutableList.of(artifact));
  }

  /**
   * test data provider for helm template precedence test value of template variable foo is as below
   * default value in chart's value.yaml - bar_default value in overrides - bar_overrides value in
   * external value.yaml - bar_external
   *
   * @return test data for helm template precedence test
   */
  private static Stream<Arguments> helmOverridesPriorityTestData() {
    /*

    */
    return Stream.of(
        // default values.yml + overrides through --values + no external values yaml -> value of foo
        // is from overrides
        Arguments.of(FOO_VALUE_OVERRIDES, 1, TemplateRenderer.HELM3, FOO_VALUE_OVERRIDES, false),
        // default values.yml + overrides through --values + no external values yaml -> value of foo
        // is from overrides
        Arguments.of(FOO_VALUE_OVERRIDES, 1, TemplateRenderer.HELM2, FOO_VALUE_OVERRIDES, false),
        // default values.yml + overrides through --set-string + no external values yaml -> value of
        // foo is from overrides
        Arguments.of(FOO_VALUE_OVERRIDES, 0, TemplateRenderer.HELM3, FOO_VALUE_OVERRIDES, false),
        Arguments.of(FOO_VALUE_OVERRIDES, 0, TemplateRenderer.HELM2, FOO_VALUE_OVERRIDES, false),
        // default values.yml + empty overrides + no external values yaml -> value of foo is from
        // default
        Arguments.of(EMPTY_OVERRIDES, 0, TemplateRenderer.HELM3, FOO_VALUE_DEFAULT, false),
        Arguments.of(EMPTY_OVERRIDES, 0, TemplateRenderer.HELM2, FOO_VALUE_DEFAULT, false),
        // default values.yml + overrides through --values +  external values yaml -> value of foo
        // is from overrides
        Arguments.of(FOO_VALUE_OVERRIDES, 1, TemplateRenderer.HELM3, FOO_VALUE_OVERRIDES, true),
        Arguments.of(FOO_VALUE_OVERRIDES, 1, TemplateRenderer.HELM2, FOO_VALUE_OVERRIDES, true),
        // default values.yml + overrides through --set-string +  external values yaml -> value of
        // foo is from overrides
        Arguments.of(FOO_VALUE_OVERRIDES, 0, TemplateRenderer.HELM3, FOO_VALUE_OVERRIDES, true),
        Arguments.of(FOO_VALUE_OVERRIDES, 0, TemplateRenderer.HELM2, FOO_VALUE_OVERRIDES, true),
        // default values.yml + empty overrides +  external values yaml -> value of foo is from
        // external values yaml
        Arguments.of(EMPTY_OVERRIDES, 0, TemplateRenderer.HELM3, FOO_VALUE_EXTERNAL, true),
        Arguments.of(EMPTY_OVERRIDES, 0, TemplateRenderer.HELM2, FOO_VALUE_EXTERNAL, true));
  }

  /**
   * Tests the priority of Helm overrides based on different input scenarios. foo is bar_default in
   * Chart's values yml, bar_overrides in overrides map and bar_external in external values yml
   * file.
   *
   * @param overrideValue the value to override in the Helm chart. If blank, no overrides are set.
   * @param overridesFileThreshold Please refer
   *     RoscoHelmConfigurationProperties#overridesFileThreshold doc
   * @param helmVersion the version of Helm being tested (e.g., HELM2, HELM3).
   * @param expectedHelmTemplateOutputValue the expected value of 'foo' in the Helm template output.
   * @param addExternalValuesFile flag to turn off/on the inclusion of external values yml file in
   *     helm template command
   * @throws Exception if any error occurs during file handling, or processing the Helm template.
   */
  @ParameterizedTest(name = "{displayName} - [{index}] {arguments}")
  @MethodSource("helmOverridesPriorityTestData")
  void testHelmOverridesPriority(
      String overrideValue,
      int overridesFileThreshold,
      TemplateRenderer helmVersion,
      String expectedHelmTemplateOutputValue,
      boolean addExternalValuesFile)
      throws Exception {
    if (!overrideValue.isBlank()) {
      bakeManifestRequest.setOverrides(Map.of("foo", overrideValue));
    }
    bakeManifestRequest.setTemplateRenderer(helmVersion);
    Path tempDir = Files.createTempDirectory("tempDir");
    Path external_values_path =
        Paths.get(getClass().getClassLoader().getResource("values_external.yaml").toURI());

    addTestHelmChartToPath(tempDir);
    roscoHelmConfigurationProperties.setOverridesFileThreshold(overridesFileThreshold);
    when(artifactDownloader.downloadArtifact(any()))
        .thenReturn(createGzippedTarballFromPath(tempDir));
    if (addExternalValuesFile) {
      Mockito.doReturn(List.of(external_values_path))
          .when(helmTemplateUtils)
          .getValuePaths(anyList(), any(BakeManifestEnvironment.class));
    }

    MvcResult result =
        webAppMockMvc
            .perform(
                post("/api/v2/manifest/bake/" + helmVersion)
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .characterEncoding(StandardCharsets.UTF_8.toString())
                    .content(objectMapper.writeValueAsString(bakeManifestRequest)))
            .andDo(print())
            .andExpect(status().isOk())
            .andReturn();
    Map<String, String> map =
        objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
    assertThat(new String(Base64.getDecoder().decode(map.get("reference"))))
        .isEqualTo(String.format(TEMPLATE_OUTPUT_FORMAT, expectedHelmTemplateOutputValue));
  }

  /**
   * Make a gzipped tarball of all files in a path
   *
   * @param rootPath the root path of the tarball
   * @return an InputStream containing the gzipped tarball
   */
  InputStream createGzippedTarballFromPath(Path rootPath) throws IOException {
    ArrayList<Path> filePathsToAdd =
        Files.walk(rootPath, FileVisitOption.FOLLOW_LINKS)
            .filter(path -> !path.equals(rootPath))
            .collect(Collectors.toCollection(ArrayList::new));
    // See https://commons.apache.org/proper/commons-compress/examples.html#Common_Archival_Logic
    // for background
    try (ByteArrayOutputStream os = new ByteArrayOutputStream();
        GzipCompressorOutputStream gzo = new GzipCompressorOutputStream(os);
        TarArchiveOutputStream tarArchive = new TarArchiveOutputStream(gzo)) {
      for (Path path : filePathsToAdd) {
        TarArchiveEntry tarEntry =
            new TarArchiveEntry(path.toFile(), rootPath.relativize(path).toString());
        tarArchive.putArchiveEntry(tarEntry);
        if (path.toFile().isFile()) {
          try (InputStream fileInputStream = Files.newInputStream(path)) {
            IOUtils.copy(fileInputStream, tarArchive);
          }
        }
        tarArchive.closeArchiveEntry();
      }
      tarArchive.finish();
      gzo.finish();
      return new ByteArrayInputStream(os.toByteArray());
    }
  }

  /**
   * Adds a test Helm chart to a specified path. This method creates necessary Helm chart files such
   * as Chart.yaml, values.yaml, and a sample template. The values.yaml file sets default values for
   * the chart, in this case, it sets the value of 'foo' to 'bar'. The templates/foo.yaml file is a
   * sample Helm template that uses the 'foo' value.
   *
   * @param path The root directory path where the Helm chart files will be created.
   * @throws IOException If there is an issue creating the files i/o in the specified path.
   */
  static void addTestHelmChartToPath(Path path) throws IOException {
    addFile(
        path,
        "Chart.yaml",
        "apiVersion: v1\n"
            + "name: example\n"
            + "description: chart for testing\n"
            + "version: 0.1\n"
            + "engine: gotpl\n");
    addFile(path, "values.yaml", "foo: " + FOO_VALUE_DEFAULT + "\n");
    addFile(
        path,
        "templates/foo.yaml",
        "labels:\n"
            + "helm.sh/chart: {{ .Chart.Name }}-{{ .Chart.Version }}\n"
            + "foo: {{.Values.foo}}");
  }

  /**
   * Create a new file in the temp directory
   *
   * @param path path of the file to create (relative to the temp directory's root)
   * @param content content of the file, or null for an empty file
   */
  static void addFile(Path tempDir, String path, String content) throws IOException {
    Path pathToCreate = tempDir.resolve(path);
    pathToCreate.toFile().getParentFile().mkdirs();
    Files.write(pathToCreate, content.getBytes());
  }
}
