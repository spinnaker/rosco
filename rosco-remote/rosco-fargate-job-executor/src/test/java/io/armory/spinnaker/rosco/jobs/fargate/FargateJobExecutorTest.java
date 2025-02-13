package io.armory.spinnaker.rosco.jobs.fargate;

import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

import io.armory.spinnaker.rosco.jobs.fargate.model.FargateConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.StopTaskRequest;

public class FargateJobExecutorTest {

  @Mock private EcsClient ecsMock;

  @Mock private FargateConfig fargateConfigMock;

  @Mock private FargateJobExecutor fargateJobExecutorMock;

  @BeforeEach
  public void setup() {
    initMocks(this);

    when(fargateConfigMock.getCluster()).thenReturn("cluster-1");
    when(fargateJobExecutorMock.jobExists(anyString())).thenReturn(true).thenReturn(false);

    ReflectionTestUtils.setField(fargateJobExecutorMock, "fargateConfig", fargateConfigMock);
    ReflectionTestUtils.setField(fargateJobExecutorMock, "ecs", ecsMock);
  }

  @Test
  public void test_cancelling_only_existing_jobs() {

    doCallRealMethod().when(fargateJobExecutorMock).cancelJob(anyString());

    fargateJobExecutorMock.cancelJob("1");
    fargateJobExecutorMock.cancelJob("1");

    verify(ecsMock, times(1)).stopTask(any(StopTaskRequest.class));
  }
}
