package com.netflix.spinnaker.rosco.providers.tencent.config;

import com.netflix.spinnaker.rosco.api.BakeOptions.BaseImage;
import com.netflix.spinnaker.rosco.api.BakeRequest.CloudProviderType;
import com.netflix.spinnaker.rosco.api.BakeRequest.VmType;
import com.netflix.spinnaker.rosco.providers.registry.CloudProviderBakeHandlerRegistry;
import com.netflix.spinnaker.rosco.providers.tencent.TencentBakeHandler;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Data
@Configuration
@ConditionalOnProperty("tencent.enabled")
@ComponentScan("com.netflix.spinnaker.rosco.providers.tencent")
public class RoscoTencentConfiguration {

  @Autowired private CloudProviderBakeHandlerRegistry cloudProviderBakeHandlerRegistry;
  @Autowired private TencentBakeHandler tencentBakeHandler;

  @Bean
  @ConfigurationProperties("tencent.bakery-defaults")
  public TencentBakeryDefaults tencentBakeryDefaults(
      @Value("${tencent.bakeryDefaults.defaultVirtualizationType:hvm}")
          VmType defaultVirtualizationType) {
    TencentBakeryDefaults defaults = new TencentBakeryDefaults();
    defaults.setDefaultVirtualizationType(defaultVirtualizationType);
    return defaults;
  }

  @PostConstruct
  public void init() {
    cloudProviderBakeHandlerRegistry.register(CloudProviderType.tencent, tencentBakeHandler);
  }

  public CloudProviderBakeHandlerRegistry getCloudProviderBakeHandlerRegistry() {
    return cloudProviderBakeHandlerRegistry;
  }

  @Data
  public static class TencentBakeryDefaults {

    private String secretId;
    private String secretKey;
    private String subnetId;
    private String vpcId;
    private Boolean associatePublicIpAddress;
    private String templateFile;
    private VmType defaultVirtualizationType;
    private List<TencentOSVirtualizationSettings> baseImages = new ArrayList<>();
  }

  @Data
  public static class TencentOSVirtualizationSettings {

    private BaseImage baseImage;
    private List<TencentVirtualizationSettings> virtualizationSettings = new ArrayList<>();
  }

  @Data
  public static class TencentVirtualizationSettings {
    private String region;
    private String virtualizationType;
    private String instanceType;
  }
}
