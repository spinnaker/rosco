package com.netflix.spinnaker.rosco.manifests;

import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.rosco.services.ClouddriverService;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.stereotype.Component;
import retrofit.RetrofitError;
import retrofit.client.Response;

@Component
@Slf4j
public final class ArtifactDownloaderImpl implements ArtifactDownloader {
  private final ClouddriverService clouddriverService;
  private final RetrySupport retrySupport = new RetrySupport();

  public ArtifactDownloaderImpl(ClouddriverService clouddriverService) {
    this.clouddriverService = clouddriverService;
  }

  public void downloadArtifact(Artifact artifact, Path targetFile) throws IOException {
    try (OutputStream outputStream = Files.newOutputStream(targetFile)) {
      Response response =
          retrySupport.retry(() -> clouddriverService.fetchArtifact(artifact), 5, 1000, true);
      if (response.getBody() != null) {
        try (InputStream inputStream = response.getBody().in()) {
          IOUtils.copy(inputStream, outputStream);
        } catch (IOException e) {
          throw new IOException(
              String.format(
                  "Failed to read input stream of downloaded artifact: %s. Error: %s",
                  artifact, e.getMessage()));
        }
      }
    } catch (RetrofitError e) {
      throw new IOException(
          String.format("Failed to download artifact: %s. Error: %s", artifact, e.getMessage()));
    }
  }
}
