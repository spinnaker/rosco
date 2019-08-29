package com.netflix.spinnaker.rosco.manifests;

import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.kork.web.exceptions.InvalidRequestException;
import com.netflix.spinnaker.rosco.services.ClouddriverService;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;
import javax.xml.bind.DatatypeConverter;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.springframework.stereotype.Component;
import retrofit.client.Response;

@Component
@Slf4j
public class TemplateUtils {
  private final ClouddriverService clouddriverService;
  private RetrySupport retrySupport = new RetrySupport();

  public TemplateUtils(ClouddriverService clouddriverService) {
    this.clouddriverService = clouddriverService;
  }

  private String nameFromReference(String reference) {
    try {
      MessageDigest md = MessageDigest.getInstance("MD5");
      return DatatypeConverter.printHexBinary(md.digest(reference.getBytes()));
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("Failed to save bake manifest: " + e.getMessage(), e);
    }
  }

  protected Path downloadArtifactToTmpFile(BakeManifestEnvironment env, Artifact artifact)
      throws IOException {
    if (artifact.getReference() == null) {
      throw new InvalidRequestException("Input artifact has an empty 'reference' field.");
    }
    File targetFile = env.getStagingPath().resolve(nameFromReference(artifact.getReference())).toFile();
    downloadArtifact(artifact, targetFile);
    return targetFile.toPath();
  }

  protected void downloadArtifactToTmpFileStructure(
      BakeManifestEnvironment env, Artifact artifact, String referenceBaseURL) throws IOException {
    if (artifact.getReference() == null) {
      throw new InvalidRequestException("Input artifact has an empty 'reference' field.");
    }
    // strip the base reference url to get the full path that the file should be written to
    // example: https://api.github.com/repos/org/repo/contents/kustomize.yml == kustomize.yml
    Path artifactFileName = Paths.get(artifact.getReference().replace(referenceBaseURL, ""));
    Path artifactFilePath = env.getStagingPath().resolve(artifactFileName);
    // ensure file write doesn't break out of the staging directory ex. ../etc
    Path artifactParentDirectory = artifactFilePath.getParent();
    if (!artifactParentDirectory.startsWith(env.getStagingPath())) {
      throw new IllegalStateException("attempting to create a file outside of the staging path.");
    }
    Files.createDirectories(artifactParentDirectory);
    File newFile = artifactFilePath.toFile();
    downloadArtifact(artifact, newFile);
  }


  private void downloadArtifact(Artifact artifact, File targetFile) throws IOException {
    try (OutputStream outputStream = new FileOutputStream(targetFile)) {
      Response response =
          retrySupport.retry(() -> clouddriverService.fetchArtifact(artifact), 5, 1000, true);
      if (response.getBody() != null) {
        try (InputStream inputStream = response.getBody().in()) {
          IOUtils.copy(inputStream, outputStream);
        }
      }
    }
  }

  public static class BakeManifestEnvironment {
    @Getter
    private final Path stagingPath =
        Paths.get(System.getProperty("java.io.tmpdir"), UUID.randomUUID().toString());

    public BakeManifestEnvironment() {
      boolean success = stagingPath.toFile().mkdirs();
      if (!success) {
        log.warn("Failed to make directory " + stagingPath + "...");
      }
    }

    public void cleanup() {
      try {
        FileUtils.deleteDirectory(stagingPath.toFile());
      } catch (IOException e) {
        throw new RuntimeException(
            "Failed to cleanup bake manifest environment: " + e.getMessage(), e);
      }
    }
  }
}
