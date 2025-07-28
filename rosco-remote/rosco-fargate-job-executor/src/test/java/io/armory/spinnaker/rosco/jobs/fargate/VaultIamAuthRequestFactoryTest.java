package io.armory.spinnaker.rosco.jobs.fargate;

import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.*;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProviderChain;
import io.armory.spinnaker.rosco.jobs.fargate.model.FargateConfig;
import io.armory.spinnaker.rosco.jobs.fargate.model.FargateVaultConfig;
import java.net.URISyntaxException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

public class VaultIamAuthRequestFactoryTest {

  VaultIamAuthRequestFactory authRequestFactory;

  @Mock AWSCredentialsProviderChain credentialsProviderChain;

  @BeforeEach
  public void before() throws URISyntaxException {
    initMocks(this);
    var fargateConfig =
        FargateConfig.builder()
            .vault(
                FargateVaultConfig.builder().address("https://foo.com").iamAuthMount("aws").build())
            .build();
    authRequestFactory = new VaultIamAuthRequestFactory(fargateConfig, credentialsProviderChain);

    var creds =
        new AWSCredentials() {
          @Override
          public String getAWSAccessKeyId() {
            return "AWSAccessKeyId";
          }

          @Override
          public String getAWSSecretKey() {
            return "AWSSecretKey";
          }
        };

    when(credentialsProviderChain.getCredentials()).thenReturn(creds);
  }

  @Test
  public void test_that_createVaultIamAuthRequest_fetches_credentials_for_each_factory_call() {
    var request1 = authRequestFactory.createVaultIamAuthRequest("foo");
    var request2 = authRequestFactory.createVaultIamAuthRequest("foo");
    Mockito.verify(credentialsProviderChain, times(2)).getCredentials();
  }
}
