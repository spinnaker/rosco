package io.armory.spinnaker.rosco.jobs.fargate.model;

import javax.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FargateVaultConfig {
  @NotEmpty private String address;
  @NotEmpty private String token;

  @NotEmpty @Builder.Default private String iamAuthMount = "aws";

  @Min(1)
  @Max(60)
  @Builder.Default
  private Integer openTimeoutSeconds = 10;

  @Min(1)
  @Max(60)
  @Builder.Default
  private Integer readTimeoutSeconds = 5;

  @Min(0)
  @Max(10)
  @Builder.Default
  private Integer maxRetries = 5;

  @Min(500)
  @Max(10000)
  @Builder.Default
  private Integer retryIntervalMilliseconds = 3000;
}
