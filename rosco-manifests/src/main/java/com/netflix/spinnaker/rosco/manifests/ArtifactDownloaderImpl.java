package com.netflix.spinnaker.rosco.manifests;

import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.kork.exceptions.SpinnakerException;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import com.netflix.spinnaker.rosco.services.ClouddriverService;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ResponseBody;
import org.apache.commons.io.IOUtils;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public final class ArtifactDownloaderImpl implements ArtifactDownloader {
  private final ClouddriverService clouddriverService;
  private final RetrySupport retrySupport = new RetrySupport();

  public ArtifactDownloaderImpl(ClouddriverService clouddriverService) {
    this.clouddriverService = clouddriverService;
  }

  public InputStream downloadArtifact(Artifact artifact) throws IOException {
    ResponseBody response =
        retrySupport.retry(
            () -> Retrofit2SyncCall.execute(clouddriverService.fetchArtifact(artifact)),
            5,
            1000,
            true);
    if (response == null) {
      throw new IOException("Failure to fetch artifact: empty response");
    }
    return response.byteStream();
  }

  public void downloadArtifactToFile(Artifact artifact, Path targetFile) throws IOException {
    try (OutputStream outputStream = Files.newOutputStream(targetFile)) {
      try (InputStream inputStream = downloadArtifact(artifact)) {
        IOUtils.copy(inputStream, outputStream);
      } catch (IOException e) {
        throw new IOException(
            String.format(
                "Failed to read input stream of downloaded artifact: %s. Error: %s",
                artifact, e.getMessage()),
            e);
      }
    } catch (SpinnakerException e) {
      throw e.newInstance(downloadFailureMessage(artifact, e));
    }
  }

  private String downloadFailureMessage(Artifact artifact, SpinnakerException e) {
    return String.format("Failed to download artifact: %s. Error: %s", artifact, e.getMessage());
  }
}
