package com.netflix.spinnaker.rosco.manifests.kustomize

import com.netflix.spinnaker.kork.artifacts.model.Artifact
import com.netflix.spinnaker.rosco.manifests.kustomize.mapping.ConfigMapGenerator
import com.netflix.spinnaker.rosco.manifests.kustomize.mapping.Kustomization
import com.netflix.spinnaker.rosco.services.ClouddriverService
import spock.lang.Specification


class KustomizeTemplateUtilsSpec extends Specification {

    def "getFilesFromArtifact returns a list of files to download based on a kustomization"() {
        given:
        def referenceBase = "https://api.github.com/repos/org/repo/contents/base"
        def clouddriverService = Mock(ClouddriverService)
        def kustomizationFileReader = Mock(KustomizationFileReader)
        def kustomizationTemplateUtils = new KustomizeTemplateUtils(kustomizationFileReader, clouddriverService)
        def baseArtifact = Artifact.builder()
                .name("base/kustomization.yml")
                .reference(referenceBase + "/kustomization.yml")
                .artifactAccount("github")
                .build()

        when:
        def filesToFetch = kustomizationTemplateUtils.getFilesFromArtifact(baseArtifact)

        then:
        kustomizationFileReader.getKustomization(_, "kustomization.yml") >> {
            Kustomization k = new Kustomization()
            k.setResources(Arrays.asList("deployment.yml", "service.yml"))
            k.setKustomizationFilename("kustomization.yml")
            return k
        }
        filesToFetch.sort() == [referenceBase.concat("/deployment.yml"),
                                referenceBase.concat("/kustomization.yml"),
                                referenceBase.concat("/service.yml")].sort()
    }

    def "getFilesFromSubFolder returns a list of files where one of the resources is referencing another kustomization"() {
        given:
        def referenceBase = "https://api.github.com/repos/org/repo/contents/base"
        def clouddriverService = Mock(ClouddriverService)
        def kustomizationFileReader = Mock(KustomizationFileReader)
        def kustomizationTemplateUtils = new KustomizeTemplateUtils(kustomizationFileReader, clouddriverService)
        def baseArtifact = Artifact.builder()
                .name("base/kustomization.yml")
                .reference(referenceBase + "/kustomization.yml")
                .artifactAccount("github")
                .build()

        when:
        def filesToFetch = kustomizationTemplateUtils.getFilesFromArtifact(baseArtifact)

        then:
        // the base artifact supplies deployment.yml, service.yml and production (a subdirectory)
        // production supplies configMap.yml
        kustomizationFileReader.getKustomization(_ as Artifact, _ as String) >> { Artifact a, String s ->
            Kustomization k = new Kustomization()
            if (a.getName() == "base") {
                k.setResources(Arrays.asList("deployment.yml", "service.yml", "production"))
            } else if (a.getName() == "base/production") {
                k.setResources(Arrays.asList("configMap.yml"))
            }
            k.setKustomizationFilename(s)
            return k
        }
        filesToFetch.sort() == [
                referenceBase + "/kustomization.yml",
                referenceBase + "/deployment.yml",
                referenceBase + "/service.yml",
                referenceBase + "/production/kustomization.yml",
                referenceBase + "/production/configMap.yml"
        ].sort()
    }

    def "getFilesFromParent returns a list of files where one of the resources is referencing another kustomization"() {
        given:
        def referenceBase = "https://api.github.com/repos/kubernetes-sigs/kustomize/contents/"
        def clouddriverService = Mock(ClouddriverService)
        def kustomizationFileReader = Mock(KustomizationFileReader)
        def kustomizationTemplateUtils = new KustomizeTemplateUtils(kustomizationFileReader, clouddriverService)
        def baseArtifact = Artifact.builder()
                .name("examples/ldap/overlays/production/kustomization.yaml")
                .reference(referenceBase + "examples/ldap/overlays/production/kustomization.yaml")
                .artifactAccount("github")
                .build()

        when:
        def filesToFetch = kustomizationTemplateUtils.getFilesFromArtifact(baseArtifact)

        then:
        // the base artifact supplies deployment.yml, service.yml and production (a subdirectory)
        // production supplies deployment.yaml, ../../base up two levels and provides a ConfigMapGenerator
        // which supplies a env.startup.txt file
        kustomizationFileReader.getKustomization(_ as Artifact, _ as String) >> { Artifact a, String s ->
            Kustomization k = new Kustomization()
            if (a.getName() == "examples/ldap/overlays/production") {
                k.setResources(Arrays.asList("../../base"))
                k.setPatchesStrategicMerge(Arrays.asList("deployment.yaml"))
            } else if (a.getName() == "examples/ldap/base") {
                k.setResources(Arrays.asList("deployment.yaml", "service.yaml"))
                List<ConfigMapGenerator> lcm = new ArrayList<>();
                ConfigMapGenerator cmg = new ConfigMapGenerator();
                cmg.setFiles(Arrays.asList("env.startup.txt"))
                lcm.add(cmg)
                k.setConfigMapGenerator(lcm)
            }
            k.setKustomizationFilename(s)
            return k
        }
        filesToFetch.sort() == [
                referenceBase + "examples/ldap/overlays/production/deployment.yaml",
                referenceBase + "examples/ldap/overlays/production/kustomization.yaml",
                referenceBase + "examples/ldap/base/service.yaml",
                referenceBase + "examples/ldap/base/deployment.yaml",
                referenceBase + "examples/ldap/base/kustomization.yaml",
                referenceBase + "examples/ldap/base/env.startup.txt"
        ].sort()
    }

    def "getFilesFromSameFolder returns a list of files where one of the resources is referencing to a kustomization"() {
        given:
        def referenceBase = "https://api.github.com/repos/kubernetes-sigs/kustomize/contents/"
        def clouddriverService = Mock(ClouddriverService)
        def kustomizationFileReader = Mock(KustomizationFileReader)
        def kustomizationTemplateUtils = new KustomizeTemplateUtils(kustomizationFileReader, clouddriverService)
        def baseArtifact = Artifact.builder()
                .name("examples/helloWorld/kustomization.yaml")
                .reference(referenceBase + "examples/helloWorld/kustomization.yaml")
                .artifactAccount("github")
                .build()

        when:
        def filesToFetch = kustomizationTemplateUtils.getFilesFromArtifact(baseArtifact)

        then:
        // the base artifact supplies deployment.yml, service.yml and configMap.yaml
        kustomizationFileReader.getKustomization(_ as Artifact, _ as String) >> { Artifact a, String s ->
            Kustomization k = new Kustomization()
            if (a.getName() == "examples/helloWorld") {
                k.setResources(Arrays.asList("deployment.yaml", "service.yaml", "configMap.yaml"))
            }
            k.setKustomizationFilename(s)
            return k
        }
        filesToFetch.sort() == [
                referenceBase + "examples/helloWorld/deployment.yaml",
                referenceBase + "examples/helloWorld/service.yaml",
                referenceBase + "examples/helloWorld/configMap.yaml",
                referenceBase + "examples/helloWorld/kustomization.yaml"
        ].sort()
    }

    def "getFilesFromMixedFolders returns a list of files where one of the resources is referencing another kustomization (5)"() {
        given:
        def referenceBase = "https://api.github.com/repos/kubernetes-sigs/kustomize/contents/"
        def clouddriverService = Mock(ClouddriverService)
        def kustomizationFileReader = Mock(KustomizationFileReader)
        def kustomizationTemplateUtils = new KustomizeTemplateUtils(kustomizationFileReader, clouddriverService)
        def baseArtifact = Artifact.builder()
                .name("examples/multibases/kustomization.yaml")
                .reference(referenceBase + "examples/multibases/kustomization.yaml")
                .artifactAccount("github")
                .build()

        when:
        def filesToFetch = kustomizationTemplateUtils.getFilesFromArtifact(baseArtifact)

        then:
        kustomizationFileReader.getKustomization(_ as Artifact, _ as String) >> { Artifact a, String s ->
            Kustomization k = new Kustomization()
            if (a.getName() == "examples/multibases") {
                k.setResources(Arrays.asList("dev", "staging", "production"))
            } else if (a.getName() == "examples/multibases/dev") {
                k.setResources(Arrays.asList("../base"))
            } else if (a.getName() == "examples/multibases/staging") {
                k.setResources(Arrays.asList("../base"))
            } else if (a.getName() == "examples/multibases/production") {
                k.setResources(Arrays.asList("../base"))
            } else if (a.getName() == "examples/multibases/base") {
                k.setResources(Arrays.asList("pod.yaml"))
            }
            k.setKustomizationFilename(s)
            return k
        }
        filesToFetch.sort() == [
                referenceBase + "examples/multibases/dev/kustomization.yaml",
                referenceBase + "examples/multibases/staging/kustomization.yaml",
                referenceBase + "examples/multibases/production/kustomization.yaml",
                referenceBase + "examples/multibases/base/kustomization.yaml",
                referenceBase + "examples/multibases/base/pod.yaml",
                referenceBase + "examples/multibases/kustomization.yaml"
        ].sort()
    }

    def "isFolder checks if a string looks like a folder"() {
        given:
        def kustomizationTemplateUtils = new KustomizeTemplateUtils(Mock(KustomizationFileReader), Mock(ClouddriverService))

        when:
        def isFolder = kustomizationTemplateUtils.isFolder(path)

        then:
        isFolder == result

        where:
        path              | result
        "../sibling"      | true
        "child"           | true
        "file.file"       | false
        "child/file.file" | false
    }
}
