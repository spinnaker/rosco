package io.armory.spinnaker.rosco.jobs.fargate.model;

import java.util.List;
import javax.annotation.Nullable;
import javax.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.regions.Region;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FargateConfig {

  /**
   * How long the fargate task can run before it is forcible killed and counted as a failure. a
   * floating point number with an optional suffix: 's' for seconds (the default), 'm' for minutes,
   * 'h' for hours or 'd' for days. A duration of 0 disables the associated timeout.
   */
  @Builder.Default @NotEmpty private String timeoutMinutes = "30";

  @Builder.Default @NotEmpty private Region region = Region.of("us-west-2");
  @NotEmpty private List<String> subnets;
  @Builder.Default @NotEmpty private String jobImage = "armory/rosco-remote-fargate-job:latest";

  @Nullable private String executionRoleArn;

  @Nullable private String cluster;
  @Builder.Default @NotEmpty private String logGroup = "rosco-jobs";
  @Builder.Default @NotEmpty private String logPrefix = "rosco";
  @Builder.Default @NotEmpty private String jobContainerName = "bake-job";

  /**
   * Sets the mem available for the task, has constraints around CPU, see the javadocs for the task
   * builder {@link
   * software.amazon.awssdk.services.ecs.model.RegisterTaskDefinitionRequest.Builder#memory(String)}
   */
  @Builder.Default @NotEmpty private String memory = "0.5 GB";

  /**
   * Sets the mem available for the task, has constraints around CPU, see the javadocs for the task
   * builder {@link
   * software.amazon.awssdk.services.ecs.model.RegisterTaskDefinitionRequest.Builder#cpu(String)} `
   */
  @Builder.Default @NotEmpty private String cpu = ".25 vCPU";

  @NotNull private FargateVaultConfig vault;
}
