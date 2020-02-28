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

package com.netflix.spinnaker.rosco.providers.yandex.config;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class YandexConfigurationProperties {
  private List<Account> accounts = new ArrayList<>();

  @Data
  public static class Account {
    private String name;
    private String folder;
    private String jsonPath;
  }
}
