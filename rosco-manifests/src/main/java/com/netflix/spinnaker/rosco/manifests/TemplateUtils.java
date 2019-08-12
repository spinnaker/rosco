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
public abstract class TemplateUtils {
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
    Path path =
        Paths.get(env.getStagingPath().toString(), nameFromReference(artifact.getReference()));
    downloadArtifact(artifact, path.toString());
    return path;
  }

  protected void downloadArtifactToTmpFileStructure(
      BakeManifestEnvironment env, Artifact artifact, String referenceBaseURL) throws IOException {
    if (artifact.getReference() == null) {
      throw new InvalidRequestException("Input artifact has an empty 'reference' field.");
    }
    Path artifactPath = Paths.get(artifact.getReference().replace(referenceBaseURL, ""));
    Path tmpPath =
        Paths.get(env.getStagingPath().toString().concat(artifactPath.getParent().toString()));
    Files.createDirectories(tmpPath);
    File newfile = new File(env.getStagingPath().toString().concat(artifactPath.toString()));
    if (!newfile.createNewFile()) {
      throw new IOException("creating file " + newfile.getName() + "failed.");
    }
    downloadArtifact(artifact, newfile.getPath());
  }

  private void downloadArtifact(Artifact artifact, String path) throws IOException {
    try (OutputStream outputStream = new FileOutputStream(path)) {
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
