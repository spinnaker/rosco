package io.armory.spinnaker.rosco.jobs.fargate;

import com.netflix.spinnaker.rosco.config.RoscoPackerConfigurationProperties;
import com.netflix.spinnaker.rosco.providers.util.PackerCommandFactory;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;

public class FargatePackerCommandFactory implements PackerCommandFactory {

  @Autowired RoscoPackerConfigurationProperties roscoPackerConfigurationProperties;

  /**
   * Compile a list of strings which can be combine and later passed to Packer. It will ignore null
   * or blank parameters, include the var file if provided, and will always include the packer
   * option to timestamp log lines. and
   *
   * @param baseCommand
   * @param parameterMap
   * @param absoluteVarFilePath
   * @param absoluteTemplateFilePath
   * @return command as a List
   */
  @Override
  public List<String> buildPackerCommand(
      String baseCommand,
      Map<String, String> parameterMap,
      String absoluteVarFilePath,
      String absoluteTemplateFilePath) {
    var packerCommand =
        new ArrayList<>(
            Arrays.asList(baseCommand, "packer", "build", "-color=false", "-timestamp-ui"));
    packerCommand.addAll(roscoPackerConfigurationProperties.getAdditionalParameters());

    for (String key : parameterMap.keySet()) {
      if (key != null && !key.isBlank()) {
        // This should always be a String according to the Type for parameterMap values, however,
        // booleans are also supplied.
        // In groovy implementations, there is no runtime error, but in Java there is. This is the
        // work around.
        Object value = parameterMap.get(key);
        String strValue = String.valueOf(value);

        if (value != null && !strValue.isBlank()) {
          String keyValuePair = key + "=" + strValue.trim();

          packerCommand.add("-var");
          packerCommand.add(keyValuePair);
        }
      }
    }

    if (absoluteVarFilePath != null && !absoluteVarFilePath.isBlank()) {
      packerCommand.add("-var-file=" + absoluteVarFilePath);
    }

    packerCommand.add(absoluteTemplateFilePath);

    return packerCommand.stream()
        .filter(str -> str != null && !str.isBlank())
        .collect(Collectors.toList());
  }
}
