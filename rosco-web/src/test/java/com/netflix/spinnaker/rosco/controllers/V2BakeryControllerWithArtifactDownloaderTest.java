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
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Getter;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.jetbrains.annotations.NotNull;
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

  public static final String SIMPLE_TEMPLATE_VARIABLE_KEY = "foo";
  public static final String NESTED_TEMPLATE_VARIABLE_KEY = "foo1.test";
  public static final String INDEXED_TEMPLATE_VARIABLE_KEY = "foo2[0]";
  public static final String DOTTED_TEMPLATE_VARIABLE_KEY_NON_NESTED = "foo3\\.foo4";
  public static final String ARRAY_TEMPLATE_VARIABLE = "foo5";

  /**
   * Enumerates predefined sets of Helm chart values used in testing to verify the precedence and
   * application of various value sources during the Helm chart rendering process. Each enum value
   * represents a different scenario, including default values, overrides, and external value files.
   */
  @Getter
  public enum HelmTemplateValues {
    /**
     * Represents default values defined within a Helm chart's values.yaml file. These are the
     * fallback values used when no overrides are provided.
     */
    DEFAULT(
        Map.of(
            SIMPLE_TEMPLATE_VARIABLE_KEY,
            "bar_default",
            NESTED_TEMPLATE_VARIABLE_KEY,
            "bar1_default",
            INDEXED_TEMPLATE_VARIABLE_KEY,
            "bar2_default",
            DOTTED_TEMPLATE_VARIABLE_KEY_NON_NESTED,
            "bar3_default",
            ARRAY_TEMPLATE_VARIABLE,
            "bar5_default")),
    /**
     * Represents user-provided overrides that can be passed to Helm via the '--values' flag or the
     * '--set' command. These values are meant to override the default values specified in the
     * chart's values.yaml.
     */
    OVERRIDES(
        Map.of(
            SIMPLE_TEMPLATE_VARIABLE_KEY,
            1000000,
            NESTED_TEMPLATE_VARIABLE_KEY,
            999999,
            INDEXED_TEMPLATE_VARIABLE_KEY,
            "bar2_overrides",
            DOTTED_TEMPLATE_VARIABLE_KEY_NON_NESTED,
            "bar3_overrides",
            ARRAY_TEMPLATE_VARIABLE,
            "{bar5_overrides}")),

    OVERRIDES_STRING_LARGE_NUMBER(
        Map.of(
            SIMPLE_TEMPLATE_VARIABLE_KEY,
            "1000000",
            NESTED_TEMPLATE_VARIABLE_KEY,
            999999,
            INDEXED_TEMPLATE_VARIABLE_KEY,
            "bar2_overrides",
            DOTTED_TEMPLATE_VARIABLE_KEY_NON_NESTED,
            "bar3_overrides",
            ARRAY_TEMPLATE_VARIABLE,
            "{bar5_overrides}")),

    OVERRIDES_NO_LARGE_NUMBERS(
        Map.of(
            SIMPLE_TEMPLATE_VARIABLE_KEY,
            999999,
            NESTED_TEMPLATE_VARIABLE_KEY,
            999999,
            INDEXED_TEMPLATE_VARIABLE_KEY,
            "bar2_overrides",
            DOTTED_TEMPLATE_VARIABLE_KEY_NON_NESTED,
            "bar3_overrides",
            ARRAY_TEMPLATE_VARIABLE,
            "{bar5_overrides}")),
    OVERRIDES_NEGATIVE_NUMBERS(
        Map.of(
            SIMPLE_TEMPLATE_VARIABLE_KEY,
            -1000000,
            NESTED_TEMPLATE_VARIABLE_KEY,
            -999999,
            INDEXED_TEMPLATE_VARIABLE_KEY,
            "bar2_overrides",
            DOTTED_TEMPLATE_VARIABLE_KEY_NON_NESTED,
            "bar3_overrides",
            ARRAY_TEMPLATE_VARIABLE,
            "{bar5_overrides}")),

    /** Represents expected helm template output with scientific notion. */
    TEMPLATE_OUTPUT_WITH_SCIENTIFIC_NOTION(
        Map.of(
            SIMPLE_TEMPLATE_VARIABLE_KEY,
            // In helm2, any numeric value greater than or equal to 1,000,000 will be treated as a
            // float
            // for both --set and --values. Consequently, the Helm template output value
            // will be in scientific notation.
            "1e+06",
            NESTED_TEMPLATE_VARIABLE_KEY,
            999999,
            INDEXED_TEMPLATE_VARIABLE_KEY,
            "bar2_overrides",
            DOTTED_TEMPLATE_VARIABLE_KEY_NON_NESTED,
            "bar3_overrides",
            ARRAY_TEMPLATE_VARIABLE,
            "{bar5_overrides}")),

    TEMPLATE_OUTPUT_WITH_NEGATIVE_SCIENTIFIC_NOTION(
        Map.of(
            SIMPLE_TEMPLATE_VARIABLE_KEY,
            // In helm2, any numeric value greater than or equal to 1,000,000 will be treated as a
            // float
            // for both --set and --values. Consequently, the Helm template output value
            // will be in scientific notation.
            "-1e+06",
            NESTED_TEMPLATE_VARIABLE_KEY,
            -999999,
            INDEXED_TEMPLATE_VARIABLE_KEY,
            "bar2_overrides",
            DOTTED_TEMPLATE_VARIABLE_KEY_NON_NESTED,
            "bar3_overrides",
            ARRAY_TEMPLATE_VARIABLE,
            "{bar5_overrides}")),

    /**
     * Represents values from an external source, such as a separate values file not included within
     * the Helm chart itself. These values are meant to simulate the scenario where values are
     * provided from an external file during the Helm chart rendering process.
     */
    EXTERNAL(
        Map.of(
            SIMPLE_TEMPLATE_VARIABLE_KEY,
            "bar_external",
            NESTED_TEMPLATE_VARIABLE_KEY,
            "bar1_external",
            INDEXED_TEMPLATE_VARIABLE_KEY,
            "bar2_external",
            DOTTED_TEMPLATE_VARIABLE_KEY_NON_NESTED,
            "bar3_external",
            ARRAY_TEMPLATE_VARIABLE,
            "bar5_external")),
    /**
     * Represents an empty map of values, used to test the scenario where no overrides are provided,
     * and the default values within the chart's values.yaml should prevail.
     */
    EMPTY(Collections.emptyMap());

    private final Map<String, Object> values;

    HelmTemplateValues(Map<String, Object> values) {
      this.values = values;
    }
  }

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
        // default values.yml + overrides through --values + no external values yaml -> values of
        // helm variables
        // is from overrides
        Arguments.of(
            HelmTemplateValues.OVERRIDES,
            1,
            TemplateRenderer.HELM3,
            HelmTemplateValues.OVERRIDES,
            false,
            false),
        Arguments.of(
            HelmTemplateValues.OVERRIDES_NO_LARGE_NUMBERS,
            1,
            TemplateRenderer.HELM3,
            HelmTemplateValues.OVERRIDES_NO_LARGE_NUMBERS,
            false,
            false),

        // default values.yml + overrides through --values + no external values yaml -> values of
        // helm variables
        // is from overrides
        Arguments.of(
            HelmTemplateValues.OVERRIDES,
            1,
            TemplateRenderer.HELM2,
            HelmTemplateValues.OVERRIDES,
            false,
            false),
        // default values.yml + overrides through --set-string + no external values yaml -> values
        // of helm variables
        //  is from overrides
        Arguments.of(
            HelmTemplateValues.OVERRIDES,
            0,
            TemplateRenderer.HELM3,
            HelmTemplateValues.OVERRIDES,
            false,
            false),
        Arguments.of(
            HelmTemplateValues.OVERRIDES,
            0,
            TemplateRenderer.HELM2,
            HelmTemplateValues.OVERRIDES,
            false,
            false),
        // default values.yml + empty overrides + no external values yaml -> values of helm
        // variables is from
        // default
        Arguments.of(
            HelmTemplateValues.EMPTY,
            0,
            TemplateRenderer.HELM3,
            HelmTemplateValues.DEFAULT,
            false,
            false),
        Arguments.of(
            HelmTemplateValues.EMPTY,
            0,
            TemplateRenderer.HELM2,
            HelmTemplateValues.DEFAULT,
            false,
            false),
        // default values.yml + overrides through --values +  external values yaml -> values of helm
        // variables
        // is from overrides
        Arguments.of(
            HelmTemplateValues.OVERRIDES,
            1,
            TemplateRenderer.HELM3,
            HelmTemplateValues.OVERRIDES,
            true,
            false),
        Arguments.of(
            HelmTemplateValues.OVERRIDES,
            1,
            TemplateRenderer.HELM2,
            HelmTemplateValues.OVERRIDES,
            true,
            false),
        // default values.yml + overrides through --set-string +  external values yaml -> values of
        // helm variables
        // is from overrides
        Arguments.of(
            HelmTemplateValues.OVERRIDES,
            0,
            TemplateRenderer.HELM3,
            HelmTemplateValues.OVERRIDES,
            true,
            false),
        Arguments.of(
            HelmTemplateValues.OVERRIDES,
            0,
            TemplateRenderer.HELM2,
            HelmTemplateValues.OVERRIDES,
            true,
            false),
        // default values.yml + empty overrides +  external values yaml -> values of helm variables
        // is from
        // external values yaml
        Arguments.of(
            HelmTemplateValues.EMPTY,
            0,
            TemplateRenderer.HELM3,
            HelmTemplateValues.EXTERNAL,
            true,
            false),
        Arguments.of(
            HelmTemplateValues.EMPTY,
            0,
            TemplateRenderer.HELM2,
            HelmTemplateValues.EXTERNAL,
            true,
            false),

        // default values.yml + overrides through --values + no external values yaml -> values of
        // helm variables
        // is from overrides
        Arguments.of(
            HelmTemplateValues.OVERRIDES,
            1,
            TemplateRenderer.HELM3,
            HelmTemplateValues.OVERRIDES,
            false,
            true),
        Arguments.of(
            HelmTemplateValues.OVERRIDES_STRING_LARGE_NUMBER,
            1,
            TemplateRenderer.HELM3,
            HelmTemplateValues.OVERRIDES_STRING_LARGE_NUMBER,
            false,
            true),
        Arguments.of(
            HelmTemplateValues.OVERRIDES_NEGATIVE_NUMBERS,
            1,
            TemplateRenderer.HELM3,
            HelmTemplateValues.OVERRIDES_NEGATIVE_NUMBERS,
            false,
            true),
        Arguments.of(
            HelmTemplateValues.OVERRIDES_NEGATIVE_NUMBERS,
            1,
            TemplateRenderer.HELM2,
            HelmTemplateValues.TEMPLATE_OUTPUT_WITH_NEGATIVE_SCIENTIFIC_NOTION,
            false,
            true),
        // default values.yml + overrides through --values + no external values yaml -> values of
        // helm variables
        // is from overrides
        Arguments.of(
            HelmTemplateValues.OVERRIDES,
            1,
            TemplateRenderer.HELM2,
            HelmTemplateValues.TEMPLATE_OUTPUT_WITH_SCIENTIFIC_NOTION,
            false,
            true),
        Arguments.of(
            HelmTemplateValues.OVERRIDES_STRING_LARGE_NUMBER,
            1,
            TemplateRenderer.HELM2,
            HelmTemplateValues.TEMPLATE_OUTPUT_WITH_SCIENTIFIC_NOTION,
            false,
            true),
        // default values.yml + overrides through --set-string + no external values yaml -> values
        // of helm variables
        //  is from overrides
        Arguments.of(
            HelmTemplateValues.OVERRIDES,
            0,
            TemplateRenderer.HELM3,
            HelmTemplateValues.OVERRIDES,
            false,
            true),
        Arguments.of(
            HelmTemplateValues.OVERRIDES,
            0,
            TemplateRenderer.HELM2,
            HelmTemplateValues.TEMPLATE_OUTPUT_WITH_SCIENTIFIC_NOTION,
            false,
            true),
        // default values.yml + empty overrides + no external values yaml -> values of helm
        // variables is from
        // default
        Arguments.of(
            HelmTemplateValues.EMPTY,
            0,
            TemplateRenderer.HELM3,
            HelmTemplateValues.DEFAULT,
            false,
            true),
        Arguments.of(
            HelmTemplateValues.EMPTY,
            0,
            TemplateRenderer.HELM2,
            HelmTemplateValues.DEFAULT,
            false,
            true),
        // default values.yml + overrides through --values +  external values yaml -> values of helm
        // variables
        // is from overrides
        Arguments.of(
            HelmTemplateValues.OVERRIDES,
            1,
            TemplateRenderer.HELM3,
            HelmTemplateValues.OVERRIDES,
            true,
            true),
        Arguments.of(
            HelmTemplateValues.OVERRIDES,
            1,
            TemplateRenderer.HELM2,
            HelmTemplateValues.TEMPLATE_OUTPUT_WITH_SCIENTIFIC_NOTION,
            true,
            true),
        // default values.yml + overrides through --set-string +  external values yaml -> values of
        // helm variables
        // is from overrides
        Arguments.of(
            HelmTemplateValues.OVERRIDES,
            0,
            TemplateRenderer.HELM3,
            HelmTemplateValues.OVERRIDES,
            true,
            true),
        Arguments.of(
            HelmTemplateValues.OVERRIDES,
            0,
            TemplateRenderer.HELM2,
            HelmTemplateValues.TEMPLATE_OUTPUT_WITH_SCIENTIFIC_NOTION,
            true,
            true),
        // default values.yml + empty overrides +  external values yaml -> values of helm variables
        // is from
        // external values yaml
        Arguments.of(
            HelmTemplateValues.EMPTY,
            0,
            TemplateRenderer.HELM3,
            HelmTemplateValues.EXTERNAL,
            true,
            true),
        Arguments.of(
            HelmTemplateValues.EMPTY,
            0,
            TemplateRenderer.HELM2,
            HelmTemplateValues.EXTERNAL,
            true,
            true));
  }

  /**
   * Tests the priority of Helm overrides based on different input scenarios, using
   * HelmTemplateValues to define both input and expectedTemplateValues configurations. This method
   * evaluates how different types of Helm value configurations (default, overrides, and external)
   * are applied and prioritized during the Helm chart rendering process.
   *
   * @param inputOverrides The HelmTemplateValues enum representing the set of values to be used as
   *     input for the Helm chart baking process. This includes any overrides or default values that
   *     should be applied to the template rendering.
   * @param overridesFileThreshold An integer representing the threshold size for overrides files,
   *     as defined in RoscoHelmConfigurationProperties. This influences how overrides are
   *     processed.
   * @param helmVersion The version of Helm being tested (e.g., TemplateRenderer.HELM2,
   *     TemplateRenderer.HELM3), which may affect the rendering behavior and the handling of values
   *     and overrides.
   * @param expectedTemplateValues The HelmTemplateValues enum representing the
   *     expectedTemplateValues set of values after the Helm chart rendering process. This is used
   *     to verify that the correct values are applied based on the input configuration and Helm
   *     version.
   * @param addExternalValuesFile A boolean flag indicating whether an external values YAML file
   *     should be included in the helm template command. This allows testing the effect of external
   *     value files on the rendering outcome.
   * @throws Exception if any error occurs during file handling, processing the Helm template, or if
   *     assertions fail due to unexpected rendering results.
   */
  @ParameterizedTest(name = "{displayName} - [{index}] {arguments}")
  @MethodSource("helmOverridesPriorityTestData")
  void testHelmOverridesPriority(
      HelmTemplateValues inputOverrides,
      int overridesFileThreshold,
      TemplateRenderer helmVersion,
      HelmTemplateValues expectedTemplateValues,
      boolean addExternalValuesFile,
      boolean rawOverrides)
      throws Exception {
    bakeManifestRequest.setOverrides(inputOverrides.values);
    bakeManifestRequest.setRawOverrides(rawOverrides);
    bakeManifestRequest.setTemplateRenderer(helmVersion);
    Path tempDir = Files.createTempDirectory("tempDir");
    Path external_values_path = getFilePathFromClassPath("values_external.yaml");

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
        .isEqualTo(
            String.format(
                readFileFromClasspath("expected_template.yaml") + "\n",
                expectedTemplateValues.getValues().get(SIMPLE_TEMPLATE_VARIABLE_KEY),
                expectedTemplateValues.getValues().get(NESTED_TEMPLATE_VARIABLE_KEY),
                expectedTemplateValues.getValues().get(INDEXED_TEMPLATE_VARIABLE_KEY),
                expectedTemplateValues.getValues().get(DOTTED_TEMPLATE_VARIABLE_KEY_NON_NESTED),
                expectedTemplateValues
                            .getValues()
                            .get(ARRAY_TEMPLATE_VARIABLE)
                            .toString()
                            .startsWith("{")
                        && expectedTemplateValues
                            .getValues()
                            .get(ARRAY_TEMPLATE_VARIABLE)
                            .toString()
                            .endsWith("}")
                    ? "bar5_overrides"
                    : expectedTemplateValues.getValues().get(ARRAY_TEMPLATE_VARIABLE).toString()));
  }

  @NotNull
  private Path getFilePathFromClassPath(String fileName) throws URISyntaxException {
    return Paths.get(getClass().getClassLoader().getResource(fileName).toURI());
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

    addFile(path, "Chart.yaml", readFileFromClasspath("Chart.yaml"));
    addFile(path, "values.yaml", readFileFromClasspath("values.yaml"));
    addFile(path, "templates/foo.yaml", readFileFromClasspath("foo.yaml"));
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

  private static String readFileFromClasspath(String fileName) throws IOException {
    // Obtain the URL of the file from the classpath
    URL fileUrl = Thread.currentThread().getContextClassLoader().getResource(fileName);
    if (fileUrl == null) {
      throw new IOException("File not found in classpath: " + fileName);
    }

    // Convert URL to a Path and read the file content
    return Files.lines(Paths.get(fileUrl.getPath()), StandardCharsets.UTF_8)
        .collect(Collectors.joining("\n"));
  }
}
