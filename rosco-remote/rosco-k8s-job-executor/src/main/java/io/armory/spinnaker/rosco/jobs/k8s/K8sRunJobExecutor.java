package io.armory.spinnaker.rosco.jobs.k8s;

import static java.util.Optional.ofNullable;

import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.rosco.api.BakeStatus;
import com.netflix.spinnaker.rosco.jobs.JobExecutor;
import com.netflix.spinnaker.rosco.jobs.JobRequest;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.BatchV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Slf4j
@Primary
@Component
public class K8sRunJobExecutor implements JobExecutor {

  private final long timeoutMinutes;

  private final BatchV1Api batchV1Api;
  private final CoreV1Api coreV1Api;
  private final String defaultJobImage;
  private final String jobNamespace = "rosco-jobs";
  private final V1ConfigMap roscoFilesConfigMap;
  private final String configDir;
  private static final String JOB_NAME_TEMPLATE = "rosco-job-%s";

  private static final Map<String, String> parametersToEnvVarsMap =
      new ImmutableMap.Builder<String, String>()
          .put("aws_access_key", "AWS_ACCESS_KEY_ID")
          .put("aws_secret_key", "AWS_SECRET_ACCESS_KEY")
          .put("aws_session_token", "AWS_SESSION_TOKEN")
          .put("aws_region", "AWS_DEFAULT_REGION")
          .build();

  public K8sRunJobExecutor(
      @Value("${rosco.jobs.k8s.default-job-image}") String defaultJobImage,
      @Value("${rosco.jobs.k8s.timeout-minutes:30}") long timeoutMinutes,
      @Value("${rosco.config-dir}") String configDir,
      BatchV1Api batchV1Api,
      CoreV1Api coreV1Api) {

    this.defaultJobImage = defaultJobImage;
    this.timeoutMinutes = timeoutMinutes;
    this.configDir = configDir;
    this.batchV1Api = batchV1Api;
    this.coreV1Api = coreV1Api;

    // Create config map out packer files
    try {
      roscoFilesConfigMap = createConfigMapFromConfigDir(configDir);
    } catch (Exception e) {
      throw new RuntimeException("Failed to create config map from rosco config dir api", e);
    }
  }

  private V1ConfigMap createConfigMapFromConfigDir(String configDirPath)
      throws ApiException, IOException {
    var configMapData =
        Files.walk(Paths.get(configDirPath))
            .filter(file -> Files.isRegularFile(file) && Files.isReadable(file))
            .collect(
                Collectors.toMap(
                    file -> file.getFileName().toString(),
                    file -> {
                      try {
                        return Files.readString(file, StandardCharsets.UTF_8);
                      } catch (IOException e) {
                        throw new RuntimeException(
                            String.format(
                                "Failed to read configuration file: '%s' as UTF-8 text",
                                file.toString()),
                            e);
                      }
                    }));

    var configMap =
        new V1ConfigMapBuilder()
            .withNewMetadata()
            .withGenerateName("rosco-config")
            .endMetadata()
            .addToData(configMapData)
            .build();

    try {
      return coreV1Api.createNamespacedConfigMap(jobNamespace, configMap, "true", null, null);

    } catch (ApiException e) {
      var msg =
          String.format(
              "Failed to create config map out of local config files. K8s Response - code: %s, body: %s",
              e.getCode(), e.getResponseBody());
      throw new RuntimeException(msg, e);
    }
  }

  @Override
  public String startJob(JobRequest jobRequest) {
    var jobId = jobRequest.getJobId();

    if (jobRequest.getTokenizedCommand().size() == 0) {
      throw new IllegalArgumentException(
          String.format(
              "No tokenizedCommand specified for %s. (executionId: %s)",
              jobId, jobRequest.getExecutionId()));
    }

    log.info(
        "Executing {} with tokenized command: {}. (executionId: {})",
        jobId,
        String.join(", ", jobRequest.getMaskedTokenizedCommand()),
        jobRequest.getExecutionId());

    var jobName = String.format(JOB_NAME_TEMPLATE, jobId);

    try {
      var configMapName =
          ofNullable(roscoFilesConfigMap.getMetadata())
              .orElseThrow(
                  () ->
                      new RuntimeException(
                          "The config map name should never be null, something has gone terribly wrong"))
              .getName();

      var configMapVolumeItems =
          ofNullable(roscoFilesConfigMap.getData())
              .orElseThrow(
                  () ->
                      new RuntimeException(
                          "The config map data should never be null, something has gone terribly wrong"))
              .keySet()
              .stream()
              .map(path -> new V1KeyToPathBuilder().withKey(path).withPath(path).build())
              .collect(Collectors.toList());

      var job =
          new V1JobBuilder()
              .withNewMetadata()
              .withName(jobName)
              .withLabels(
                  Map.of(
                      "jobId", jobRequest.getJobId(),
                      "executionId", ofNullable(jobRequest.getExecutionId()).orElse("unset"),
                      "rosco-bake", "true"))
              .endMetadata()
              .withNewSpec()
              .withNewTemplate()
              .withNewSpec()
              .withContainers(
                  new V1ContainerBuilder()
                      .withName("rosco-job")
                      .withImage(defaultJobImage)
                      .withCommand(jobRequest.getTokenizedCommand())
                      .withEnv(mapParametersToEnvVars(jobRequest.getTokenizedCommand()))
                      .withVolumeMounts(
                          new V1VolumeMountBuilder()
                              .withName("configuration-files")
                              .withMountPath(configDir)
                              .build())
                      .build())
              .withRestartPolicy("Never")
              .withVolumes(
                  new V1VolumeBuilder()
                      .withName("configuration-files")
                      .withConfigMap(
                          new V1ConfigMapVolumeSourceBuilder()
                              .withName(configMapName)
                              .withItems(configMapVolumeItems)
                              .build())
                      .build())
              .endSpec()
              .endTemplate()
              .withBackoffLimit(0)
              .withActiveDeadlineSeconds(TimeUnit.MINUTES.toSeconds(timeoutMinutes))
              .endSpec()
              .build();

      batchV1Api.createNamespacedJob(jobNamespace, job, null, null, null);

    } catch (ApiException e) {
      var msg =
          String.format(
              "Failed to start remote K8s Job. K8s Response - code: %s, body: %s",
              e.getCode(), e.getResponseBody());
      throw new RuntimeException(msg, e);
    }

    return jobId;
  }

  /**
   * Maps key parameters that we know also need to be env vars, such as AWS credentials for the
   * Packer to automatically work, so that users do not have to manually set credentials in their
   * templates.
   *
   * @param tokenizedCommand the job command that contains the packer args
   * @return the list of env vars to inject into the job container
   */
  private List<V1EnvVar> mapParametersToEnvVars(List<String> tokenizedCommand) {
    var parameterMap = new HashMap<String, String>();

    tokenizedCommand.forEach(
        arg -> {
          var parts = arg.split("=", 2);
          var key = parts[0];
          var value = parts.length > 1 ? parts[1] : "";
          parameterMap.put(key, value);
        });

    return parametersToEnvVarsMap.keySet().stream()
        .filter(parameterMap::containsKey)
        .map(
            parameterKey -> {
              var envVarName = parametersToEnvVarsMap.get(parameterKey);
              var envVarValue = parameterMap.get(parameterKey);
              return new V1EnvVar().name(envVarName).value(envVarValue);
            })
        .collect(Collectors.toList());
  }

  @Override
  public boolean jobExists(String jobId) {
    try {
      var maxResults = 1;
      var jobs =
          batchV1Api.listNamespacedJob(
              jobNamespace,
              null,
              null,
              null,
              null,
              "jobId=" + jobId,
              maxResults,
              null,
              null,
              null,
              null);
      var job = jobs.getItems().stream().findAny();
      return job.isPresent();
    } catch (ApiException e) {
      throw new RuntimeException("Failed to query K8s API for job id: " + jobId, e);
    }
  }

  @Override
  public BakeStatus updateJob(String jobId) {
    String executionId = "unknown";
    try {
      log.info("Finding K8s job for jobId: " + jobId);
      var jobName = String.format(JOB_NAME_TEMPLATE, jobId);
      // Fetch the status of the job from the K8s API
      V1Job job = batchV1Api.readNamespacedJob(jobName, jobNamespace, null, null, null);

      executionId =
          Optional.ofNullable(job.getMetadata())
              .map(V1ObjectMeta::getLabels)
              .map(labels -> labels.get("executionId"))
              .orElse("EXECUTION_ID_LABEL_NOT_SET");

      log.info("Polling state for {} (executionId: {})...", jobId, executionId);
      var bakeStatus = new BakeStatus();
      bakeStatus.setId(jobId);
      bakeStatus.setResource_id(jobId);

      var jobStatus = job.getStatus();
      if (jobStatus == null) { // when will this be null?
        return null;
      }

      // Fetch the list of pods for the job (there should only be 1 that matches the job name
      // selector)
      var jobPods =
          coreV1Api.listNamespacedPod(
              jobNamespace,
              null,
              null,
              null,
              null,
              "job-name=" + jobName,
              1,
              null,
              null,
              null,
              null);
      var pod = jobPods.getItems().stream().findAny();
      var podName = pod.map(V1Pod::getMetadata).map(V1ObjectMeta::getName);

      // TODO if the pod has an error status StartError or Error or CreateError etc, mark job as
      // failed rather than poll until the job timeouts

      // If we found the associated pod, fetch its logs
      podName.ifPresent(
          pn -> {
            String podLogOutput = "";
            try {
              podLogOutput =
                  coreV1Api.readNamespacedPodLog(
                      pn,
                      jobNamespace,
                      "rosco-job",
                      false,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null);
            } catch (ApiException e) {
              // TODO handle this appropriately, don't log error when pod is first creating and
              // handle error states and ensure there can't be infinite polling
              var msg =
                  String.format(
                      "Failed to fetch log data. K8s Response - code: %s, body: %s",
                      e.getCode(), e.getResponseBody());
              log.error(msg, e);
            }

            bakeStatus.setLogsContent(podLogOutput);
            bakeStatus.setOutputContent(podLogOutput);
          });

      if (ofNullable(jobStatus.getFailed()).orElse(0) > 0) {

        // Delete the K8s Job.
        try {
          batchV1Api.deleteNamespacedJob(jobName, jobNamespace, null, null, 0, null, null, null);
        } catch (ApiException e) {
          // Might create an orphaned job, but the max ttl is set, so should self purge
          var msg =
              String.format(
                  "Failed to delete errored k8s job. K8s Response - code: %s, body: %s",
                  e.getCode(), e.getResponseBody());
          log.error(msg, e);
        }

        bakeStatus.setState(BakeStatus.State.CANCELED);
        bakeStatus.setResult(BakeStatus.Result.FAILURE);
        return bakeStatus;
      }

      ofNullable(jobStatus.getCompletionTime())
          .ifPresentOrElse(
              (completedAt) -> {
                // Else
                bakeStatus.setState(BakeStatus.State.COMPLETED);
                bakeStatus.setResult(BakeStatus.Result.SUCCESS);
              },
              // if the complete time isn't set yet, the job isn't done
              () -> bakeStatus.setState(BakeStatus.State.RUNNING));

      // return the updated status object
      return bakeStatus;
    } catch (ApiException e) {
      if (!e.getResponseBody().contains("is waiting to start: ContainerCreating")) {
        // Don't barf all over the logs while the container is starting
        log.error("Failed to update {} (executionId: {})", jobId, executionId, e);
      }
      return null;
    } catch (Exception e) {
      return null;
    }
  }

  @Override
  public void cancelJob(String jobId) {
    var jobName = String.format(JOB_NAME_TEMPLATE, jobId);
    log.info("Canceling job {} ...", jobId);
    // Delete the K8s Job.
    try {
      batchV1Api.deleteNamespacedJob(jobName, jobNamespace, null, null, 0, null, null, null);
    } catch (ApiException e) {
      var msg =
          String.format(
              "Failed to cancel k8s job. K8s Response - code: %s, body: %s",
              e.getCode(), e.getResponseBody());
      throw new RuntimeException(msg, e);
    }
  }

  /**
   * This is only used by the local executor to add a shutdown hook so that jobs finished executing
   * before termination. Since K8s is remote, we will just always return 0 and treat this as a
   * no-op, we could query the K8s API and get the total number of all jobs if desired later.
   *
   * @return 0
   */
  @Override
  public int runningJobCount() {
    return 0;
  }
}
