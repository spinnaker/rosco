package io.armory.spinnaker.rosco.jobs.fargate

import com.netflix.spinnaker.rosco.config.RoscoPackerConfigurationProperties
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class FargatePackerCommandFactorySpec extends Specification {

    @Shared
    FargatePackerCommandFactory packerCommandFactory = new FargatePackerCommandFactory(
            roscoPackerConfigurationProperties: new RoscoPackerConfigurationProperties()
    )

    @Unroll
    void "packerCommand handles baseCommand as string, null or empty: baseCommand is #baseCommand"() {
        setup:
            def parameterMap = [
                    something: something
            ]

        when:
            def packerCommand = packerCommandFactory.buildPackerCommand(baseCommand, parameterMap, null, "")

        then:
            packerCommand == expectedPackerCommand

        where:
            something | baseCommand | expectedPackerCommand
            "sudo"    | "sudo"      | ["sudo", "packer", "build", "-color=false", "-timestamp-ui", "-var", "something=sudo"]
            "null"    | null        | ["packer", "build", "-color=false", "-timestamp-ui", "-var", "something=null"]
            "empty"   | ""          | ["packer", "build", "-color=false", "-timestamp-ui", "-var", "something=empty"]
            "empty"   | "  "        | ["packer", "build", "-color=false", "-timestamp-ui", "-var", "something=empty"]
    }

    @Unroll
    void "packerCommand includes -varFileName only when 'varFile' is specified; varFile is #varFile"() {
        setup:
            def parameterMap = [
                    something: "some-var"
            ]

        when:
            def packerCommand = packerCommandFactory.buildPackerCommand("", parameterMap, varFile, "")

        then:
            packerCommand == expectedPackerCommand

        where:
            varFile            | expectedPackerCommand
            null               | ["packer", "build", "-color=false", "-timestamp-ui", "-var", "something=some-var"]
            ""                 | ["packer", "build", "-color=false", "-timestamp-ui", "-var", "something=some-var"]
            "    "             | ["packer", "build", "-color=false", "-timestamp-ui", "-var", "something=some-var"]
            "someVarFile.json" | ["packer", "build", "-color=false", "-timestamp-ui", "-var", "something=some-var", "-var-file=someVarFile.json"]
    }

    @Unroll
    void "packerCommand includes parameter with non-quoted string while ignoring null and empty key/values or boolean"() {

        when:
            def packerCommand = packerCommandFactory.buildPackerCommand("", parameterMap, null, "")

        then:
            packerCommand == expectedPackerCommand

        where:
            parameterMap                      | expectedPackerCommand
            [packages: "package1 package2"]   | ["packer", "build", "-color=false", "-timestamp-ui", "-var", "packages=package1 package2"]
            [packages: "package1 package2",
             path: "/some/path"]              | ["packer", "build", "-color=false", "-timestamp-ui", "-var", "packages=package1 package2"
                                                ,"-var" , "path=/some/path"]
            [packages: "package1 package2",
             path: null]                      | ["packer", "build", "-color=false", "-timestamp-ui", "-var", "packages=package1 package2"]
            [packages: " package1 "]          | ["packer", "build", "-color=false", "-timestamp-ui", "-var", "packages=package1"]
            ["aws_param": true]               | ["packer", "build", "-color=false", "-timestamp-ui", "-var", "aws_param=true"]
            [packages: ""]                    | ["packer", "build", "-color=false", "-timestamp-ui"]
            [packages: "   "]                 | ["packer", "build", "-color=false", "-timestamp-ui"]
            [packages: null]                  | ["packer", "build", "-color=false", "-timestamp-ui"]
            ["": "value"]                     | ["packer", "build", "-color=false", "-timestamp-ui"]
            [(null): "value"]                 | ["packer", "build", "-color=false", "-timestamp-ui"]
            ["    ": "value"]                 | ["packer", "build", "-color=false", "-timestamp-ui"]
    }
}
