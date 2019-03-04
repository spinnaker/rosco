/*
 * Copyright 2019 Armory
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

package com.netflix.spinnaker.rosco.manifests.jinja;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.rosco.api.BakeStatus;
import com.netflix.spinnaker.rosco.jobs.BakeRecipe;
import com.netflix.spinnaker.rosco.jobs.JobExecutor;
import com.netflix.spinnaker.rosco.jobs.JobRequest;
import com.netflix.spinnaker.rosco.manifests.BakeManifestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.netflix.spinnaker.rosco.manifests.TemplateUtils.BakeManifestEnvironment;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@Component
public class JinjaBakeManifestService implements BakeManifestService {

  @Autowired
  JobExecutor jobExecutor;

  @Autowired
  JinjaTemplateUtils templateUtils;

  @Override
  public boolean handles(String type) {
    return type.equals("jinja");
  }

  public Artifact bake(Map<String, Object> request) {
    ObjectMapper mapper = new ObjectMapper();
    JinjaBakeManifestRequest bakeManifestRequest = mapper.convertValue(request, JinjaBakeManifestRequest.class);
    BakeManifestEnvironment env = new BakeManifestEnvironment();
    BakeRecipe recipe = templateUtils.buildBakeRecipe(env, bakeManifestRequest);

    BakeStatus bakeStatus;
    try {
      JobRequest jobRequest = new JobRequest(recipe.getCommand(), new ArrayList<>(), UUID.randomUUID().toString());
      String jobId = jobExecutor.startJob(jobRequest);

      bakeStatus = jobExecutor.updateJob(jobId);
      while (bakeStatus == null || bakeStatus.getState() == BakeStatus.State.RUNNING) {
          try {
            Thread.sleep(1000);
          } catch (InterruptedException ignored){

          }
          bakeStatus = jobExecutor.updateJob(jobId);
      }

      if (bakeStatus.getResult() != BakeStatus.Result.SUCCESS) {
        throw new IllegalStateException("Bake of " + bakeManifestRequest + "failed: " + bakeStatus.getLogsContent());
      }
    } finally {
      env.cleanup();
    }

    return Artifact.builder()
      .type("embedded/base64")
      .name(bakeManifestRequest.getOutputName())
      .reference(Base64.getEncoder().encodeToString(bakeStatus.getLogsContent().getBytes()))
      .build();
  }



}
