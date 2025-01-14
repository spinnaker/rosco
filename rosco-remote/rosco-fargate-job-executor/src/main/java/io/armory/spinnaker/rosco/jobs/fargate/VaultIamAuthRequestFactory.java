package io.armory.spinnaker.rosco.jobs.fargate;

import com.amazonaws.DefaultRequest;
import com.amazonaws.auth.AWS4Signer;
import com.amazonaws.auth.AWSCredentialsProviderChain;
import com.amazonaws.http.HttpMethodName;
import com.google.common.base.Charsets;
import com.google.common.collect.LinkedHashMultimap;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.armory.spinnaker.rosco.jobs.fargate.model.FargateConfig;
import io.armory.spinnaker.rosco.jobs.fargate.model.VaultIamAuthRequest;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sts.model.StsRequest;

@Component
public class VaultIamAuthRequestFactory {

  private final String region;
  private static final String REQUEST_BODY = "Action=GetCallerIdentity&Version=2011-06-15";
  private final String authMount;
  private final AWSCredentialsProviderChain credentialsProviderChain;
  private final String vaultAddr;
  private final String endpoint;
  private final URI endpointURI;

  public VaultIamAuthRequestFactory(
      FargateConfig fargateConfig, AWSCredentialsProviderChain credentialsProviderChain)
      throws URISyntaxException {
    region = "us-east-1";
    this.credentialsProviderChain = credentialsProviderChain;
    vaultAddr = fargateConfig.getVault().getAddress();
    authMount = fargateConfig.getVault().getIamAuthMount();
    endpoint = String.format("https://sts.%s.amazonaws.com", region);
    endpointURI = new URI(endpoint);
  }

  public VaultIamAuthRequest createVaultIamAuthRequest(String role) {
    var requestsHeaders = createSignedRequestHeaders();
    return VaultIamAuthRequest.builder()
        .vaultAwsAuthRole(role)
        .vaultAwsAuthMount(authMount)
        .signedStsGetCallerIdentityRequestHeaders(requestsHeaders)
        .stsGetCallerIdentityRequestBody(REQUEST_BODY)
        .stsGetCallerIdentityRequestUrl(endpoint)
        .build();
  }

  private String createSignedRequestHeaders() {
    var credentials = credentialsProviderChain.getCredentials();
    var headers =
        Map.of(
            "X-Vault-AWS-IAM-Server-ID",
            vaultAddr.split("//")[1],
            "Content-Type",
            "application/x-www-form-urlencoded; charset=utf-8");

    var defaultRequest = new DefaultRequest<StsRequest>("sts");
    defaultRequest.setContent(new ByteArrayInputStream(REQUEST_BODY.getBytes(Charsets.UTF_8)));
    defaultRequest.setHeaders(headers);
    defaultRequest.setHttpMethod(HttpMethodName.POST);
    defaultRequest.setEndpoint(endpointURI);

    var aws4Signer = new AWS4Signer();
    aws4Signer.setServiceName(defaultRequest.getServiceName());
    aws4Signer.setRegionName(region);
    aws4Signer.sign(defaultRequest, credentials);

    var signedHeaders = LinkedHashMultimap.<String, String>create();
    var defaultRequestHeaders = defaultRequest.getHeaders();
    defaultRequestHeaders.forEach(signedHeaders::put);

    final JsonObject jsonObject = new JsonObject();
    signedHeaders
        .asMap()
        .forEach(
            (k, v) -> {
              final JsonArray array = new JsonArray();
              v.forEach(array::add);
              jsonObject.add(k, array);
            });

    return jsonObject.toString();
  }
}
