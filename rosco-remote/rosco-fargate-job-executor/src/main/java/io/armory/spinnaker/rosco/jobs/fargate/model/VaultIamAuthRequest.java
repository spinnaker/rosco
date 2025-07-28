package io.armory.spinnaker.rosco.jobs.fargate.model;

import com.google.common.base.Charsets;
import java.util.Base64;
import lombok.Builder;

@Builder
public class VaultIamAuthRequest {

  private final String vaultAwsAuthRole;
  private final String vaultAwsAuthMount;
  private final String stsGetCallerIdentityRequestUrl;
  private final String stsGetCallerIdentityRequestBody;
  private final String signedStsGetCallerIdentityRequestHeaders;

  public String getVaultAwsAuthRole() {
    return vaultAwsAuthRole;
  }

  public String getStsGetCallerIdentityRequestUrl() {
    return base64Encode(stsGetCallerIdentityRequestUrl);
  }

  public String getStsGetCallerIdentityRequestBody() {
    return base64Encode(stsGetCallerIdentityRequestBody);
  }

  public String getSignedIamRequestHeaders() {
    return base64Encode(signedStsGetCallerIdentityRequestHeaders);
  }

  public String getVaultAwsAuthMount() {
    return vaultAwsAuthMount;
  }

  private String base64Encode(String stringToEncode) {
    return Base64.getEncoder().encodeToString(stringToEncode.getBytes(Charsets.UTF_8));
  }
}
