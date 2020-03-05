/*
 * Copyright 2020 YANDEX LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.rosco.providers.yandex;

import static com.netflix.spinnaker.rosco.providers.yandex.config.RoscoYandexCloudConfiguration.*;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.base.Strings;
import com.netflix.spinnaker.rosco.api.Bake;
import com.netflix.spinnaker.rosco.api.BakeOptions;
import com.netflix.spinnaker.rosco.api.BakeOptions.BaseImage;
import com.netflix.spinnaker.rosco.api.BakeRequest;
import com.netflix.spinnaker.rosco.providers.CloudProviderBakeHandler;
import com.netflix.spinnaker.rosco.providers.util.ImageNameFactory;
import com.netflix.spinnaker.rosco.providers.yandex.config.YandexConfigurationProperties;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
public class YandexBakeHandler extends CloudProviderBakeHandler {
  private static final String IMAGE_NAME_TOKEN = "yandex: Creating image:";
  private static final String IMAGE_ID_TOKEN = "yandex: A disk image was created:";
  private static final Pattern pattern = Pattern.compile("\\(id: (.*)\\)");

  private final ImageNameFactory imageNameFactory = new ImageNameFactory();
  private final ObjectMapper objectMapper =
      new ObjectMapper()
          .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
          .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  private final YandexBakeryDefaults yandexBakeryDefaults;
  private final YandexConfigurationProperties yandexConfigurationProperties;

  @Autowired
  @Lazy
  public YandexBakeHandler(
      YandexBakeryDefaults yandexBakeryDefaults,
      YandexConfigurationProperties yandexConfigurationProperties) {
    this.yandexBakeryDefaults = yandexBakeryDefaults;
    this.yandexConfigurationProperties = yandexConfigurationProperties;
  }

  @Override
  public Object getBakeryDefaults() {
    return yandexBakeryDefaults;
  }

  @Override
  public BakeOptions getBakeOptions() {
    List<YandexOperatingSystemVirtualizationSettings> settings =
        yandexBakeryDefaults.getBaseImages();
    List<BaseImage> baseImages =
        settings.stream()
            .map(YandexOperatingSystemVirtualizationSettings::getBaseImage)
            .collect(Collectors.toList());

    BakeOptions bakeOptions = new BakeOptions();
    bakeOptions.setCloudProvider(BakeRequest.CloudProviderType.yandex.toString());
    bakeOptions.setBaseImages(baseImages);
    return bakeOptions;
  }

  @Override
  public Object findVirtualizationSettings(String region, BakeRequest bakeRequest) {
    List<YandexOperatingSystemVirtualizationSettings> settings =
        yandexBakeryDefaults.getBaseImages().stream()
            .filter(setting -> setting.getBaseImage().getId().equals(bakeRequest.getBase_os()))
            .collect(Collectors.toList());

    if (settings.isEmpty()) {
      throw new IllegalArgumentException(
          "No virtualization settings found for '" + bakeRequest.getBase_os() + "'.");
    }

    return settings.get(0).getVirtualizationSettings();
  }

  @Override
  public String getTemplateFileName(BaseImage baseImage) {
    return StringUtils.firstNonEmpty(
        baseImage.getTemplateFile(), yandexBakeryDefaults.getTemplateFile());
  }

  @Override
  public Map<String, Object> buildParameterMap(
      String region,
      Object virtualizationSettings,
      String imageName,
      BakeRequest bakeRequest,
      String appVersionStr) {
    YandexConfigurationProperties.Account account = resolveAccount(bakeRequest);

    Map<String, Object> parameterMap = new HashMap<>(20);
    parameterMap.put("yandex_folder_id", account.getFolder());
    if (StringUtils.isNotEmpty(bakeRequest.getBuild_info_url())) {
      parameterMap.put("build_info_url", bakeRequest.getBuild_info_url());
    }

    if (StringUtils.isNotEmpty(appVersionStr)) {
      parameterMap.put("appversion", appVersionStr);
    }
    parameterMap.put("yandex_target_image_name", imageName);
    parameterMap.put("yandex_service_account_key_file", account.getJsonPath()); // : null,

    YandexVirtualizationSettings settings =
        objectMapper.convertValue(virtualizationSettings, YandexVirtualizationSettings.class);

    if (settings != null) {
      if (StringUtils.isNotEmpty(settings.getSourceImageId())) {
        parameterMap.put("yandex_source_image_id", settings.getSourceImageId());
      }
      if (StringUtils.isNotEmpty(settings.getSourceImageName())) {
        parameterMap.put("yandex_source_image_name", settings.getSourceImageName());
      }
      if (StringUtils.isNotEmpty(settings.getSourceImageFamily())) {
        parameterMap.put("yandex_source_image_family", settings.getSourceImageFamily());
      }
      if (StringUtils.isNotEmpty(settings.getSourceImageFolderId())) {
        parameterMap.put("yandex_source_image_folder_id", settings.getSourceImageFolderId());
      }
    }

    return parameterMap;
  }

  @Override
  public Bake scrapeCompletedBakeResults(String region, String bakeId, String logsContent) {
    Bake bake = new Bake();
    bake.setId(bakeId);

    for (String line : logsContent.split("\\n")) {
      if (line.contains(IMAGE_NAME_TOKEN)) {
        String[] s = line.split(" ");
        bake.setImage_name(s[s.length - 1]);
      } else if (line.contains(IMAGE_ID_TOKEN)) {
        Matcher matcher = pattern.matcher(line);
        if (matcher.find() && matcher.groupCount() == 1) {
          bake.setAmi(matcher.group(1));
        }
      }
    }

    return bake;
  }

  @Override
  public String produceProviderSpecificBakeKeyComponent(String region, BakeRequest bakeRequest) {
    return region;
  }

  @Override
  public ImageNameFactory getImageNameFactory() {
    return imageNameFactory;
  }

  private YandexConfigurationProperties.Account resolveAccount(BakeRequest bakeRequest) {
    if (yandexConfigurationProperties.getAccounts().isEmpty()) {
      throw new IllegalArgumentException(
          "Could not resolve Yandex account: accounts in rosco configuration is empty.");
    }
    String accountName = bakeRequest.getAccount_name();
    if (Strings.isNullOrEmpty(accountName)) {
      return yandexConfigurationProperties.getAccounts().get(0);
    }
    return Optional.of(accountName)
        .flatMap(
            an ->
                yandexConfigurationProperties.getAccounts().stream()
                    .filter(a -> an.equals(a.getName()))
                    .findFirst())
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Could not resolve Yandex account: (account_name=$bakeRequest.account_name)."));
  }
}
