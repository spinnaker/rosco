package io.armory.spinnaker.rosco.jobs.fargate;

import static java.util.Optional.ofNullable;

import com.amazonaws.services.logs.AWSLogs;
import com.amazonaws.services.logs.AWSLogsClient;
import com.amazonaws.services.logs.model.GetLogEventsRequest;
import com.amazonaws.services.logs.model.GetLogEventsResult;
import com.amazonaws.services.logs.model.OutputLogEvent;
import com.bettercloud.vault.Vault;
import com.bettercloud.vault.VaultException;
import com.bettercloud.vault.api.Auth;
import com.bettercloud.vault.response.AuthResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.kork.annotations.VisibleForTesting;
import com.netflix.spinnaker.rosco.api.BakeStatus;
import com.netflix.spinnaker.rosco.jobs.JobExecutor;
import com.netflix.spinnaker.rosco.jobs.JobRequest;
import com.netflix.spinnaker.rosco.persistence.BakeStore;
import io.armory.spinnaker.rosco.jobs.fargate.model.FargateConfig;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.*;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;

@Slf4j
@Primary
@Component
public class FargateJobExecutor implements JobExecutor {

  private static final String LOGS_INIT_MESSAGE =
      "Hang tight, the logs stream is being initialized...";

  private static final Pattern FARGATE_TASK_ID_PATTERN =
      Pattern.compile("arn:aws:ecs:.*?:task.*/(?<taskId>.*)");

  private static final Pattern STS_ROLE_ARN =
      Pattern.compile(
          "arn:aws:sts::(?<accountId>\\d+):assumed-role/(?<roleName>.*?)/(?<sessionName>.*)");

  private final FargateConfig fargateConfig;
  private final String configDir;
  private final String awsIamRole;
  private final String awsIamRoleExternalId;
  private final Map<String, Object> configMap;
  private final Function<String, Vault> vaultClientFactory;
  private final EcsClient ecs;
  private final AWSLogs awsLogs;
  private final StsClient sts;
  private final String taskArn;
  private final ObjectMapper om = new ObjectMapper();
  private final VaultIamAuthRequestFactory vaultIamAuthRequestFactory;
  private String vaultRoleName;
  private final BakeStore bakeStore;

  public FargateJobExecutor(
      FargateConfig fargateConfig,
      @Value("${rosco.config-dir}") String configDir,
      @Value("${aws.bakeryDefaults.awsIamRole:#{null}}") String awsIamRole,
      @Value("${aws.bakeryDefaults.awsIamRoleExternalId:#{null}}") String awsIamRoleExternalId,
      @Qualifier("configMap") Map<String, Map<String, Object>> configMap,
      Function<String, Vault> vaultClientFactory,
      VaultIamAuthRequestFactory vaultIamAuthRequestFactory,
      BakeStore bakeStore) {

    this.fargateConfig = fargateConfig;
    this.configDir = configDir;
    this.awsIamRole = awsIamRole;
    this.awsIamRoleExternalId = awsIamRoleExternalId;
    this.configMap = configMap.get("getConfigMap");
    this.vaultClientFactory = vaultClientFactory;
    this.vaultIamAuthRequestFactory = vaultIamAuthRequestFactory;
    this.bakeStore = bakeStore;

    ecs = EcsClient.builder().region(fargateConfig.getRegion()).build();
    awsLogs = AWSLogsClient.builder().withRegion(fargateConfig.getRegion().toString()).build();
    sts = StsClient.builder().region(fargateConfig.getRegion()).build();

    var taskDefinitionBuilder =
        RegisterTaskDefinitionRequest.builder()
            .containerDefinitions(
                ContainerDefinition.builder()
                    .command("bash", "/opt/rosco-job/fargate-rosco-command-wrapper.sh")
                    .image(fargateConfig.getJobImage())
                    .name(fargateConfig.getJobContainerName())
                    .logConfiguration(
                        LogConfiguration.builder()
                            .logDriver(LogDriver.AWSLOGS)
                            .options(
                                Map.of(
                                    "awslogs-region", fargateConfig.getRegion().toString(),
                                    "awslogs-group", fargateConfig.getLogGroup(),
                                    "awslogs-stream-prefix", fargateConfig.getLogPrefix()))
                            .build())
                    .build())
            .family("rosco-job-task-" + UUID.randomUUID().toString())
            .networkMode(NetworkMode.AWSVPC)
            .requiresCompatibilities(Compatibility.FARGATE)
            .memory(fargateConfig.getMemory())
            .cpu(fargateConfig.getCpu());

    // Set the execution role for the task as either the explicitly defined role or the role that
    // rosco is currently running as.
    var executionRole =
        ofNullable(fargateConfig.getExecutionRoleArn())
            .or(
                () -> {
                  var callerResponse = sts.getCallerIdentity();
                  var arnMatcher = STS_ROLE_ARN.matcher(callerResponse.arn());
                  if (!arnMatcher.find()) {
                    throw new RuntimeException(
                        "Failed to extract the role name from the current sts caller identity arn");
                  }
                  this.vaultRoleName = arnMatcher.group("roleName");
                  var roleArn =
                      String.format(
                          "arn:aws:iam::%s:role/%s", callerResponse.account(), this.vaultRoleName);
                  log.info("Using {} as the execution arn", roleArn);
                  return Optional.of(roleArn);
                })
            .orElseThrow();
    taskDefinitionBuilder.executionRoleArn(executionRole);

    // We explicitly set this to null so that AWS does not give the containers an IAM role.
    // We will inject temp assumed credentials that are compatible with external ids into the
    // process from Vault
    // with our wrapper script
    taskDefinitionBuilder.taskRoleArn(null);

    var task = taskDefinitionBuilder.build();
    var registerTaskDefinitionResponse = ecs.registerTaskDefinition(task);

    taskArn = registerTaskDefinitionResponse.taskDefinition().taskDefinitionArn();
  }

  /**
   * Creates a 2x use token in vault and uses it 1x to upload the config map into vault and returns
   * the token so that the fargate job can use it the remain 1x to download the config map.
   *
   * @param jobId The id for the job
   * @return The token that will have 1 remaining use to download the config map
   */
  @VisibleForTesting
  public String writeJobContextToVault(String jobId, String jobCommand, Vault vaultClient) {
    Map<String, String> awsAssumedRoleCredentialsEnvVars;
    try {
      var roleRequestBuilder =
          AssumeRoleRequest.builder()
              .roleArn(
                  Optional.ofNullable(awsIamRole)
                      .orElseThrow(
                          () ->
                              new RuntimeException(
                                  "awsIamRole must be configured in the Armory Cloud Console under default bakary settings")))
              .roleSessionName("armory-ami-bake" + jobId);

      Optional.ofNullable(awsIamRoleExternalId)
          .ifPresent(externalId -> roleRequestBuilder.externalId(awsIamRoleExternalId));

      var roleRequest = roleRequestBuilder.build();

      var credentials = sts.assumeRole(roleRequest).credentials();
      awsAssumedRoleCredentialsEnvVars =
          Map.of(
              "AWS_ACCESS_KEY_ID", credentials.accessKeyId(),
              "AWS_SECRET_ACCESS_KEY", credentials.secretAccessKey(),
              "AWS_SESSION_TOKEN", credentials.sessionToken(),
              "AWS_DEFAULT_REGION", fargateConfig.getRegion().toString());
    } catch (Exception e) {
      var msg =
          String.format("Failed to assume role: %s for ami bake request: %s", awsIamRole, jobId);
      throw new RuntimeException(msg, e);
    }

    try {
      var authResponse =
          vaultClient
              .auth()
              .createToken(
                  new Auth.TokenRequest()
                      .displayName(String.format("rosco-job-token-%s", jobId))
                      // how long the job has to read the data using its 1x use token
                      .explicitMaxTtl("5m")
                      .renewable(false)
                      .numUses(2L)
                      .noDefaultPolicy(true));
      // Use token 1x to upload config map to cubbyhole
      var token = authResponse.getAuthClientToken();
      var clientWithLimitedToken = vaultClientFactory.apply(token);

      var context =
          Map.of(
              "configMap", configMap,
              "configDir", configDir,
              "jobCommand", jobCommand,
              "commandTimeout", String.format("%sm", fargateConfig.getTimeoutMinutes()),
              "awsCredentials", awsAssumedRoleCredentialsEnvVars);

      // Vault does weird things to sub-objects, so we can just b64 a json payload and unpack it in
      // the container.
      var b64EncodedJsonContext = Base64.getEncoder().encodeToString(om.writeValueAsBytes(context));

      // read with vault read cubbyhole/data/job-context
      clientWithLimitedToken
          .logical()
          .write(
              "cubbyhole/job-context", Map.of("base64-encoded-job-context", b64EncodedJsonContext));
      return token;
    } catch (Exception e) {
      throw new RuntimeException("Failed to write config map to Vault for job: " + jobId, e);
    }
  }

  public Vault getVaultClient(final FargateConfig fargateConfig, final String vaultRoleName) {
    var vaultConfig = fargateConfig.getVault();
    Vault vault = vaultClientFactory.apply(null);

    try {
      var vaultIamAuthRequest = vaultIamAuthRequestFactory.createVaultIamAuthRequest(vaultRoleName);
      AuthResponse response =
          vault
              .auth()
              .loginByAwsIam(
                  vaultRoleName,
                  vaultIamAuthRequest.getStsGetCallerIdentityRequestUrl(),
                  vaultIamAuthRequest.getStsGetCallerIdentityRequestBody(),
                  vaultIamAuthRequest.getSignedIamRequestHeaders(),
                  vaultIamAuthRequest.getVaultAwsAuthMount());
      vault = vaultClientFactory.apply(response.getAuthClientToken());
    } catch (VaultException e) {
      throw new RuntimeException("Failed to authenticate with IAM role for Vault.", e);
    }
    return vault;
  }

  @Override
  public String startJob(JobRequest jobRequest) {
    Vault vaultClient;
    var jobId = jobRequest.getJobId();
    var jobCommand = String.join(" ", jobRequest.getTokenizedCommand());

    vaultClient = getVaultClient(fargateConfig, vaultRoleName);

    var oneTimeUseToken = writeJobContextToVault(jobId, jobCommand, vaultClient);

    var envVars = new LinkedList<KeyValuePair>();
    envVars.add(
        KeyValuePair.builder()
            .name("VAULT_ADDR")
            .value(fargateConfig.getVault().getAddress())
            .build());
    envVars.add(KeyValuePair.builder().name("VAULT_TOKEN").value(oneTimeUseToken).build());

    var runTaskResponse =
        ecs.runTask(
            RunTaskRequest.builder()
                .launchType(LaunchType.FARGATE)
                .networkConfiguration(
                    NetworkConfiguration.builder()
                        .awsvpcConfiguration(
                            AwsVpcConfiguration.builder()
                                .assignPublicIp(AssignPublicIp.ENABLED)
                                .subnets(fargateConfig.getSubnets())
                                .build())
                        .build())
                .taskDefinition(taskArn)
                .cluster(fargateConfig.getCluster())
                .overrides(
                    TaskOverride.builder()
                        .containerOverrides(
                            ContainerOverride.builder()
                                .name(fargateConfig.getJobContainerName())
                                .environment(envVars)
                                .build())
                        .build())
                .tags(List.of(Tag.builder().key("jobId").value(jobId).build()))
                .build());

    var taskArn = runTaskResponse.tasks().stream().findFirst().orElseThrow().taskArn();
    var taskMatcher = FARGATE_TASK_ID_PATTERN.matcher(taskArn);
    if (!taskMatcher.find()) {
      throw new RuntimeException("Failed to extract task id out of task arn");
    }
    var taskId = taskMatcher.group("taskId");
    log.info("Fargate task for bake request id: {} started with id: {}", jobId, taskId);
    return taskId;
  }

  @Override
  public boolean jobExists(String taskId) {
    var describeTasksResponse =
        ecs.describeTasks(
            DescribeTasksRequest.builder()
                .cluster(fargateConfig.getCluster())
                .tasks(taskId)
                .build());
    return describeTasksResponse.tasks().size() > 0;
  }

  @Override
  public BakeStatus updateJob(String taskId) {
    // Initialize a new bake status object with default values that we will override as needed
    var bakeStatus = new BakeStatus();
    bakeStatus.setId(taskId);
    bakeStatus.setResource_id(taskId);
    bakeStatus.setState(BakeStatus.State.RUNNING);

    // Search for tasks that match our task id, in the job cluster
    var describeTasksResponse =
        ecs.describeTasks(
            DescribeTasksRequest.builder()
                .cluster(fargateConfig.getCluster())
                .tasks(taskId)
                .build());

    var taskOptional = describeTasksResponse.tasks().stream().findFirst();

    // The tasks will be empty, when we first start a job
    if (taskOptional.isEmpty()) {
      bakeStatus.setOutputContent(LOGS_INIT_MESSAGE);
      bakeStatus.setLogsContent(LOGS_INIT_MESSAGE);
      return bakeStatus;
    }
    var task = taskOptional.get();
    // https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task-lifecycle.html
    var lastStatus = task.lastStatus();

    // Populate the bake status's logs
    var logs =
        fetchLogsForTaskSafely(taskId) // Try to get the logs from cloud watch
            // fall back to grabbing them from the bake store
            .or(
                () ->
                    ofNullable(bakeStore.retrieveBakeLogsById(taskId))
                        .map(it -> it.get("logsContent")))
            .orElse(LOGS_INIT_MESSAGE);

    bakeStatus.setOutputContent(logs);
    bakeStatus.setLogsContent(logs);

    // The task is still running
    if (!lastStatus.equals("STOPPED")) {
      return bakeStatus;
    }

    boolean didError = false;
    var container = task.containers().stream().findFirst().orElseThrow();
    if (ofNullable(container.exitCode()).orElse(1) != 0) {
      didError = true;
    }

    if (didError) {
      bakeStatus.setState(BakeStatus.State.CANCELED);
      bakeStatus.setResult(BakeStatus.Result.FAILURE);
      return bakeStatus;
    }

    bakeStatus.setState(BakeStatus.State.COMPLETED);
    bakeStatus.setResult(BakeStatus.Result.SUCCESS);

    return bakeStatus;
  }

  /**
   * Fetches the complete logs for a Fargate Bake Job Task
   *
   * @param taskId The task id of the Fargate Task
   * @return The complete logs for the container as a string
   */
  private String getLogsForTask(String taskId) {
    var cloudWatchStreamName =
        String.format(
            "%s/%s/%s", fargateConfig.getLogPrefix(), fargateConfig.getJobContainerName(), taskId);
    List<OutputLogEvent> logsEvents = new LinkedList<>();
    GetLogEventsResult getLogEventsResult;
    String nextToken = null;
    do {
      getLogEventsResult =
          awsLogs.getLogEvents(
              new GetLogEventsRequest()
                  .withLogGroupName(fargateConfig.getLogGroup())
                  .withLogStreamName(cloudWatchStreamName)
                  .withNextToken(nextToken));

      logsEvents.addAll(getLogEventsResult.getEvents());

      // This assumes the stream starts at the beginning, I have an suspicion that this isn't
      // guaranteed
      nextToken = getLogEventsResult.getNextForwardToken();
    } while (!nextToken.equals((nextToken = getLogEventsResult.getNextForwardToken())));
    return logsEvents.stream().map(OutputLogEvent::getMessage).collect(Collectors.joining("\n"));
  }

  /**
   * Fetches logs for a task returning an empty optional if the logs don't exist. This happens when
   * a Fargate Task is first submitted and the log streams have not been created yet.
   *
   * @param taskId The task id of the Fargate Task
   * @return The complete logs for the container as a string if found else empty
   */
  private Optional<String> fetchLogsForTaskSafely(String taskId) {
    try {
      return ofNullable(getLogsForTask(taskId));
    } catch (com.amazonaws.services.logs.model.ResourceNotFoundException e) {
      return Optional.empty();
    }
  }

  @Override
  public void cancelJob(String taskId) {
    if (jobExists(taskId)) {
      ecs.stopTask(
          StopTaskRequest.builder()
              .cluster(fargateConfig.getCluster())
              .task(taskId)
              .reason("canceled via rosco api")
              .build());
    }
  }

  /**
   * This is only used by the local executor to add a shutdown hook so that jobs finished executing
   * before termination. Since K8s is remote, we will just always return 0 and treat this as a
   * no-op, we could query the K8s API and get the total number of all jobs if desired later.
   *
   * <p>TODO, looks like rosco loops for manifest bakes, so this might not be true? Might need an
   * in-mem map to get track of internal state :barf
   *
   * @return 0
   */
  @Override
  public int runningJobCount() {
    return 0;
  }

  @PreDestroy
  public void deregisterTaskDefinition() {
    ecs.deregisterTaskDefinition(
        DeregisterTaskDefinitionRequest.builder().taskDefinition(taskArn).build());
  }
}
