/*
 * Copyright 2018 Scopely, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.rosco.providers.util;

import org.springframework.util.FileCopyUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.inject.Inject;

public class PackerTemplateService {

  private Path tempDir = Paths.get(System.getProperty("java.io.tmpdir"));

  public PackerTemplateService() throws IOException {
    if (!Files.isDirectory(tempDir)) {
      Files.createDirectories(tempDir);
    }
  }

  public Path writeTemplateToFile(String bakeId, String template) {
    Path templateFilePath = getTemplateFilePath(bakeId);

    try {
      FileCopyUtils.copy(template.getBytes(StandardCharsets.UTF_8), templateFilePath.toFile());
    } catch (IOException e) {
      throw new IllegalStateException("Could not write Packer template to file: " + templateFilePath, e);
    }

    return templateFilePath;
  }

  public void deleteTemplateFile(String bakeId) {
    Path templateFilePath = getTemplateFilePath(bakeId);

    try {
      Files.deleteIfExists(templateFilePath);
    } catch (IOException e) {
      throw new IllegalStateException("Could not delete template file: " + templateFilePath, e);
    }
  }

  private Path getTemplateFilePath(String bakeId) {
    return tempDir.resolve(bakeId + "-template.json");
  }
}
