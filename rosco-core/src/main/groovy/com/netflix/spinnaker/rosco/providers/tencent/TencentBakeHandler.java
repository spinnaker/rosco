package com.netflix.spinnaker.rosco.providers.tencent;

import com.netflix.spinnaker.rosco.api.Bake;
import com.netflix.spinnaker.rosco.api.BakeOptions;
import com.netflix.spinnaker.rosco.api.BakeOptions.BaseImage;
import com.netflix.spinnaker.rosco.api.BakeRequest;
import com.netflix.spinnaker.rosco.api.BakeRequest.CloudProviderType;
import com.netflix.spinnaker.rosco.providers.CloudProviderBakeHandler;
import com.netflix.spinnaker.rosco.providers.tencent.config.RoscoTencentConfiguration.TencentBakeryDefaults;
import com.netflix.spinnaker.rosco.providers.util.ImageNameFactory;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
public class TencentBakeHandler extends CloudProviderBakeHandler {

  @Override
  public TencentBakeryDefaults getBakeryDefaults() {
    return tencentBakeryDefaults;
  }

  @Override
  public BakeOptions getBakeOptions() {
    BakeOptions options = new BakeOptions();
    final TencentBakeryDefaults defaults = tencentBakeryDefaults;
    options.setCloudProvider(CloudProviderType.tencent.toString());
    options.setBaseImages(
        Optional.ofNullable(defaults).map(it -> it.getBaseImages()).orElse(new ArrayList<>())
            .stream()
            .map(it -> it.getBaseImage())
            .collect(Collectors.toList()));
    return options;
  }

  @Override
  public String produceProviderSpecificBakeKeyComponent(String region, BakeRequest bakeRequest) {
    return null;
  }

  @Override
  public Object findVirtualizationSettings(String region, BakeRequest bakeRequest) {
    return null;
  }

  @Override
  public Map buildParameterMap(
      String region,
      Object virtualizationSettings,
      String imageName,
      BakeRequest bakeRequest,
      String appVersionStr) {
    Map<String, String> parameterMap = new HashMap<String, String>(2);
    parameterMap.put("tencent_region", region);
    parameterMap.put("tencent_target_image", imageName);

    if (!StringUtils.isEmpty(tencentBakeryDefaults.getSecretId())
        && !StringUtils.isEmpty(tencentBakeryDefaults.getSecretKey())) {
      parameterMap.put("tencent_secret_id", tencentBakeryDefaults.getSecretId());
      parameterMap.put("tencent_secret_key", tencentBakeryDefaults.getSecretKey());
    }

    if (!StringUtils.isEmpty(tencentBakeryDefaults.getSubnetId())) {
      parameterMap.put("tencent_subnet_id", tencentBakeryDefaults.getSubnetId());
    }

    if (!StringUtils.isEmpty(tencentBakeryDefaults.getVpcId())) {
      parameterMap.put("tencent_vpc_id", tencentBakeryDefaults.getVpcId());
    }

    if (tencentBakeryDefaults.getAssociatePublicIpAddress() != null) {
      parameterMap.put(
          "tencent_associate_public_ip_address",
          String.valueOf(tencentBakeryDefaults.getAssociatePublicIpAddress()));
    }

    if (!StringUtils.isEmpty(appVersionStr)) {
      parameterMap.put("appversion", appVersionStr);
    }

    return parameterMap;
  }

  @Override
  public String getTemplateFileName(BaseImage baseImage) {
    final Optional<String> file = Optional.ofNullable(baseImage.getTemplateFile());
    return file.orElse(tencentBakeryDefaults.getTemplateFile());
  }

  @Override
  public Bake scrapeCompletedBakeResults(final String region, String bakeId, String logsContent) {
    String amiId = "";
    String imageName = "";

    // TODO(duftler): Presently scraping the logs for the image name/id.
    // Would be better to not be reliant on the log
    // format not changing. Resolve this by storing bake details in redis
    // and querying oort for amiId from amiName.
    BufferedReader bufReader = new BufferedReader(new StringReader(logsContent));
    String line = null;
    try {
      while ((line = bufReader.readLine()) != null) {
        if (!StringUtils.isEmpty(Pattern.compile(IMAGE_NAME_TOKEN).matcher(line))) {
          String[] parts = line.split(" ");
          imageName = parts[parts.length - 1];
        } else if (!StringUtils.isEmpty(
            Pattern.compile(UNENCRYPTED_IMAGE_NAME_TOKEN).matcher(line))) {
          line = line.replaceAll(UNENCRYPTED_IMAGE_NAME_TOKEN, "").trim();
          imageName = line.split(" ")[0];
        } else if (!StringUtils.isEmpty(Pattern.compile(region + ":").matcher(line))) {
          String[] parts = line.split(" ");
          amiId = parts[parts.length - 1];
        }
      }
    } catch (IOException ioe) {
      log.error("scrapeCompletedBakeResults error", ioe);
    }

    Bake bake = new Bake();
    bake.setId(bakeId);
    bake.setAmi(amiId);
    bake.setImage_name(imageName);
    return bake;
  }

  public ImageNameFactory getImageNameFactory() {
    return imageNameFactory;
  }

  private static final String IMAGE_NAME_TOKEN = "ManagedImageName: ";
  private static final String UNENCRYPTED_IMAGE_NAME_TOKEN = "ManagedImageId: ";
  private ImageNameFactory imageNameFactory = new ImageNameFactory();
  @Autowired private TencentBakeryDefaults tencentBakeryDefaults;
}
