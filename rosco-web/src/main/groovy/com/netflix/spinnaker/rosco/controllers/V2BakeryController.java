package com.netflix.spinnaker.rosco.controllers;

import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.rosco.manifests.BakeManifestRequest;
import com.netflix.spinnaker.rosco.manifests.helm.HelmBakeManifestRequest;
import com.netflix.spinnaker.rosco.manifests.helm.HelmBakeManifestService;
import com.netflix.spinnaker.rosco.manifests.jinja.JinjaBakeManifestRequest;
import com.netflix.spinnaker.rosco.manifests.jinja.JinjaBakeManifestService;
import groovy.util.logging.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Slf4j
public class V2BakeryController {
  @Autowired
  HelmBakeManifestService helmBakeManifestService;

  @Autowired
  JinjaBakeManifestService jinjaBakeManifestService;


  @RequestMapping(value = "/api/v2/manifest/bake/helm", method = RequestMethod.POST)
  Artifact doBake(@RequestBody HelmBakeManifestRequest bakeRequest) {
    return helmBakeManifestService.bake(bakeRequest);
  }

  @RequestMapping(value = "/api/v2/manifest/bake/jinja", method = RequestMethod.POST)
  Artifact doJinjaBake(@RequestBody JinjaBakeManifestRequest bakeRequest) {
    return jinjaBakeManifestService.bake(bakeRequest);
  }
}
