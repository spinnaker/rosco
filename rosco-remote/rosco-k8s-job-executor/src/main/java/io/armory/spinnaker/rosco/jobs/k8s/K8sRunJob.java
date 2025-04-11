package io.armory.spinnaker.rosco.jobs.k8s;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class K8sRunJob {
  private String name;
  private String namespace;
  private String executionId;
  private Instant startTime;
}
