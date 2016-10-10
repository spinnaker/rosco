/*
 * Copyright 2015 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.rosco.controllers

import com.netflix.spectator.api.DefaultRegistry
import com.netflix.spinnaker.rosco.api.Bake
import com.netflix.spinnaker.rosco.api.BakeOptions
import com.netflix.spinnaker.rosco.api.BakeRequest
import com.netflix.spinnaker.rosco.api.BakeStatus
import com.netflix.spinnaker.rosco.persistence.RedisBackedBakeStore
import com.netflix.spinnaker.rosco.providers.CloudProviderBakeHandler
import com.netflix.spinnaker.rosco.providers.registry.CloudProviderBakeHandlerRegistry
import com.netflix.spinnaker.rosco.providers.registry.DefaultCloudProviderBakeHandlerRegistry
import com.netflix.spinnaker.rosco.jobs.JobExecutor
import com.netflix.spinnaker.rosco.jobs.JobRequest
import spock.lang.Specification
import spock.lang.Subject

class BakeryControllerSpec extends Specification {

  private static final String PACKAGE_NAME = "kato"
  private static final String REGION = "some-region"
  private static final String JOB_ID = "123"
  private static final String EXISTING_JOB_ID = "456"
  private static final String AMI_ID = "ami-3cf4a854"
  private static final String IMAGE_NAME = "some-image"
  private static final String BAKE_KEY = "bake:gce:ubuntu:$PACKAGE_NAME"
  private static final String BAKE_ID = "some-bake-id"
  private static final String PACKER_COMMAND = "packer build ..."
  private static final String LOGS_CONTENT = "Some logs content..."

  void 'create bake launches job and returns new status'() {
    setup:
      def cloudProviderBakeHandlerRegistryMock = Mock(CloudProviderBakeHandlerRegistry)
      def cloudProviderBakeHandlerMock = Mock(CloudProviderBakeHandler)
      def bakeStoreMock = Mock(RedisBackedBakeStore)
      def jobExecutorMock = Mock(JobExecutor)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGE_NAME,
                                        base_os: "ubuntu",
                                        cloud_provider_type: BakeRequest.CloudProviderType.gce)
      def runningBakeStatus = new BakeStatus(id: JOB_ID, resource_id: JOB_ID, state: BakeStatus.State.RUNNING)

      @Subject
      def bakeryController = new BakeryController(cloudProviderBakeHandlerRegistry: cloudProviderBakeHandlerRegistryMock,
                                                  bakeStore: bakeStoreMock,
                                                  jobExecutor: jobExecutorMock)

    when:
      def returnedBakeStatus = bakeryController.createBake(REGION, bakeRequest, null)

    then:
      1 * cloudProviderBakeHandlerRegistryMock.lookup(BakeRequest.CloudProviderType.gce) >> cloudProviderBakeHandlerMock
      1 * cloudProviderBakeHandlerMock.produceBakeKey(REGION, bakeRequest) >> BAKE_KEY
      1 * bakeStoreMock.retrieveBakeStatusByKey(BAKE_KEY) >> null
      1 * cloudProviderBakeHandlerMock.producePackerCommand(REGION, bakeRequest) >> [PACKER_COMMAND]
      1 * bakeStoreMock.acquireBakeLock(BAKE_KEY) >> true
      1 * jobExecutorMock.startJob(new JobRequest(tokenizedCommand: [PACKER_COMMAND])) >> JOB_ID
      1 * jobExecutorMock.updateJob(JOB_ID) >> runningBakeStatus
      1 * bakeStoreMock.storeNewBakeStatus(BAKE_KEY, REGION, bakeRequest, runningBakeStatus, PACKER_COMMAND) >> runningBakeStatus
      returnedBakeStatus == runningBakeStatus
  }

  void 'create bake fails fast if job executor returns CANCELED'() {
    setup:
      def cloudProviderBakeHandlerRegistryMock = Mock(CloudProviderBakeHandlerRegistry)
      def cloudProviderBakeHandlerMock = Mock(CloudProviderBakeHandler)
      def bakeStoreMock = Mock(RedisBackedBakeStore)
      def jobExecutorMock = Mock(JobExecutor)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGE_NAME,
                                        base_os: "ubuntu",
                                        cloud_provider_type: BakeRequest.CloudProviderType.gce)
      def failedBakeStatus = new BakeStatus(id: JOB_ID,
                                            resource_id: JOB_ID,
                                            state: BakeStatus.State.CANCELED,
                                            result: BakeStatus.Result.FAILURE,
                                            logsContent: "Some kind of failure...")

      @Subject
      def bakeryController = new BakeryController(cloudProviderBakeHandlerRegistry: cloudProviderBakeHandlerRegistryMock,
                                                  bakeStore: bakeStoreMock,
                                                  jobExecutor: jobExecutorMock)

    when:
      bakeryController.createBake(REGION, bakeRequest, null)

    then:
      1 * cloudProviderBakeHandlerRegistryMock.lookup(BakeRequest.CloudProviderType.gce) >> cloudProviderBakeHandlerMock
      1 * cloudProviderBakeHandlerMock.produceBakeKey(REGION, bakeRequest) >> BAKE_KEY
      1 * bakeStoreMock.retrieveBakeStatusByKey(BAKE_KEY) >> null
      1 * cloudProviderBakeHandlerMock.producePackerCommand(REGION, bakeRequest) >> [PACKER_COMMAND]
      1 * bakeStoreMock.acquireBakeLock(BAKE_KEY) >> true
      1 * jobExecutorMock.startJob(new JobRequest(tokenizedCommand: [PACKER_COMMAND])) >> JOB_ID
      1 * jobExecutorMock.updateJob(JOB_ID) >> failedBakeStatus
      IllegalArgumentException e = thrown()
      e.message == "Some kind of failure..."
  }

  void 'create bake polls for status when lock cannot be acquired'() {
    setup:
      def cloudProviderBakeHandlerRegistryMock = Mock(CloudProviderBakeHandlerRegistry)
      def cloudProviderBakeHandlerMock = Mock(CloudProviderBakeHandler)
      def bakeStoreMock = Mock(RedisBackedBakeStore)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGE_NAME,
                                        base_os: "ubuntu",
                                        cloud_provider_type: BakeRequest.CloudProviderType.gce)
      def runningBakeStatus = new BakeStatus(id: JOB_ID, resource_id: JOB_ID, state: BakeStatus.State.RUNNING)

      @Subject
      def bakeryController = new BakeryController(cloudProviderBakeHandlerRegistry: cloudProviderBakeHandlerRegistryMock,
                                                  bakeStore: bakeStoreMock)

    when:
      def returnedBakeStatus = bakeryController.createBake(REGION, bakeRequest, null)

    then:
      1 * cloudProviderBakeHandlerRegistryMock.lookup(BakeRequest.CloudProviderType.gce) >> cloudProviderBakeHandlerMock
      1 * cloudProviderBakeHandlerMock.produceBakeKey(REGION, bakeRequest) >> BAKE_KEY
      1 * bakeStoreMock.retrieveBakeStatusByKey(BAKE_KEY) >> null
      1 * cloudProviderBakeHandlerMock.producePackerCommand(REGION, bakeRequest) >> [PACKER_COMMAND]
      1 * bakeStoreMock.acquireBakeLock(BAKE_KEY) >> false
      4 * bakeStoreMock.retrieveBakeStatusByKey(BAKE_KEY) >> null
      1 * bakeStoreMock.retrieveBakeStatusByKey(BAKE_KEY) >> runningBakeStatus
      returnedBakeStatus == runningBakeStatus
  }

  void 'create bake polls for status when lock cannot be acquired, but tries for lock again if status cannot be obtained'() {
    setup:
      def cloudProviderBakeHandlerRegistryMock = Mock(CloudProviderBakeHandlerRegistry)
      def cloudProviderBakeHandlerMock = Mock(CloudProviderBakeHandler)
      def bakeStoreMock = Mock(RedisBackedBakeStore)
      def jobExecutorMock = Mock(JobExecutor)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGE_NAME,
                                        base_os: "ubuntu",
                                        cloud_provider_type: BakeRequest.CloudProviderType.gce)
      def runningBakeStatus = new BakeStatus(id: JOB_ID, resource_id: JOB_ID, state: BakeStatus.State.RUNNING)

      @Subject
      def bakeryController = new BakeryController(cloudProviderBakeHandlerRegistry: cloudProviderBakeHandlerRegistryMock,
                                                  bakeStore: bakeStoreMock,
                                                  jobExecutor: jobExecutorMock)

    when:
      def returnedBakeStatus = bakeryController.createBake(REGION, bakeRequest, null)

    then:
      1 * cloudProviderBakeHandlerRegistryMock.lookup(BakeRequest.CloudProviderType.gce) >> cloudProviderBakeHandlerMock
      1 * cloudProviderBakeHandlerMock.produceBakeKey(REGION, bakeRequest) >> BAKE_KEY
      1 * bakeStoreMock.retrieveBakeStatusByKey(BAKE_KEY) >> null
      1 * cloudProviderBakeHandlerMock.producePackerCommand(REGION, bakeRequest) >> [PACKER_COMMAND]
      1 * bakeStoreMock.acquireBakeLock(BAKE_KEY) >> false
      (10.._) * bakeStoreMock.retrieveBakeStatusByKey(BAKE_KEY) >> null
      1 * bakeStoreMock.acquireBakeLock(BAKE_KEY) >> true
      1 * jobExecutorMock.startJob(new JobRequest(tokenizedCommand: [PACKER_COMMAND])) >> JOB_ID
      1 * jobExecutorMock.updateJob(JOB_ID) >> runningBakeStatus
      1 * bakeStoreMock.storeNewBakeStatus(BAKE_KEY, REGION, bakeRequest, runningBakeStatus, PACKER_COMMAND) >> runningBakeStatus
      returnedBakeStatus == new BakeStatus(id: JOB_ID, resource_id: JOB_ID, state: BakeStatus.State.RUNNING)
  }

  void 'create bake polls for status when lock cannot be acquired, tries for lock again if status cannot be obtained, and throws exception if that fails'() {
    setup:
      def cloudProviderBakeHandlerRegistryMock = Mock(CloudProviderBakeHandlerRegistry)
      def cloudProviderBakeHandlerMock = Mock(CloudProviderBakeHandler)
      def bakeStoreMock = Mock(RedisBackedBakeStore)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGE_NAME,
                                        base_os: "ubuntu",
                                        cloud_provider_type: BakeRequest.CloudProviderType.gce)

    @Subject
      def bakeryController = new BakeryController(cloudProviderBakeHandlerRegistry: cloudProviderBakeHandlerRegistryMock,
                                                  bakeStore: bakeStoreMock)

    when:
      bakeryController.createBake(REGION, bakeRequest, null)

    then:
      1 * cloudProviderBakeHandlerRegistryMock.lookup(BakeRequest.CloudProviderType.gce) >> cloudProviderBakeHandlerMock
      1 * cloudProviderBakeHandlerMock.produceBakeKey(REGION, bakeRequest) >> BAKE_KEY
      1 * bakeStoreMock.retrieveBakeStatusByKey(BAKE_KEY) >> null
      1 * cloudProviderBakeHandlerMock.producePackerCommand(REGION, bakeRequest) >> [PACKER_COMMAND]
      1 * bakeStoreMock.acquireBakeLock(BAKE_KEY) >> false
      (10.._) * bakeStoreMock.retrieveBakeStatusByKey(BAKE_KEY) >> null
      1 * bakeStoreMock.acquireBakeLock(BAKE_KEY) >> false
      IllegalArgumentException e = thrown()
      e.message == "Unable to acquire lock and unable to determine id of lock holder for bake key 'bake:gce:ubuntu:kato'."
  }

  void 'create bake throws exception on provider that lacks registered bake handler'() {
    setup:
      def cloudProviderBakeHandlerRegistryMock = Mock(CloudProviderBakeHandlerRegistry)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGE_NAME,
                                        base_os: "ubuntu",
                                        cloud_provider_type: BakeRequest.CloudProviderType.gce)

      @Subject
      def bakeryController = new BakeryController(cloudProviderBakeHandlerRegistry: cloudProviderBakeHandlerRegistryMock)

    when:
      bakeryController.createBake(REGION, bakeRequest, null)

    then:
      1 * cloudProviderBakeHandlerRegistryMock.lookup(BakeRequest.CloudProviderType.gce) >> null
      IllegalArgumentException e = thrown()
      e.message == "Unknown provider type 'gce'."
  }

  void 'create bake returns existing status when prior bake is running'() {
    setup:
      def cloudProviderBakeHandlerRegistryMock = Mock(CloudProviderBakeHandlerRegistry)
      def cloudProviderBakeHandlerMock = Mock(CloudProviderBakeHandler)
      def bakeStoreMock = Mock(RedisBackedBakeStore)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGE_NAME,
                                        base_os: "ubuntu",
                                        cloud_provider_type: BakeRequest.CloudProviderType.gce)
      def runningBakeStatus = new BakeStatus(id: EXISTING_JOB_ID,
                                             resource_id: EXISTING_JOB_ID,
                                             state: BakeStatus.State.RUNNING)

      @Subject
      def bakeryController = new BakeryController(cloudProviderBakeHandlerRegistry: cloudProviderBakeHandlerRegistryMock,
                                                  bakeStore: bakeStoreMock,
                                                  registry: new DefaultRegistry())

    when:
      def returnedBakeStatus = bakeryController.createBake(REGION, bakeRequest, null)

    then:
      1 * cloudProviderBakeHandlerRegistryMock.lookup(BakeRequest.CloudProviderType.gce) >> cloudProviderBakeHandlerMock
      1 * cloudProviderBakeHandlerMock.produceBakeKey(REGION, bakeRequest) >> BAKE_KEY
      1 * bakeStoreMock.retrieveBakeStatusByKey(BAKE_KEY) >> runningBakeStatus
      returnedBakeStatus == runningBakeStatus
  }

  void 'create bake returns existing status when prior bake is completed and successful'() {
    setup:
      def cloudProviderBakeHandlerRegistryMock = Mock(CloudProviderBakeHandlerRegistry)
      def cloudProviderBakeHandlerMock = Mock(CloudProviderBakeHandler)
      def bakeStoreMock = Mock(RedisBackedBakeStore)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGE_NAME,
                                        base_os: "ubuntu",
                                        cloud_provider_type: BakeRequest.CloudProviderType.gce)
      def completedBakeStatus = new BakeStatus(id: EXISTING_JOB_ID,
                                               resource_id: EXISTING_JOB_ID,
                                               state: BakeStatus.State.COMPLETED,
                                               result: BakeStatus.Result.SUCCESS)

      @Subject
      def bakeryController = new BakeryController(cloudProviderBakeHandlerRegistry: cloudProviderBakeHandlerRegistryMock,
                                                  bakeStore: bakeStoreMock,
                                                  registry: new DefaultRegistry())

    when:
      def returnedBakeStatus = bakeryController.createBake(REGION, bakeRequest, null)

    then:
      1 * cloudProviderBakeHandlerRegistryMock.lookup(BakeRequest.CloudProviderType.gce) >> cloudProviderBakeHandlerMock
      1 * cloudProviderBakeHandlerMock.produceBakeKey(REGION, bakeRequest) >> BAKE_KEY
      1 * bakeStoreMock.retrieveBakeStatusByKey(BAKE_KEY) >> completedBakeStatus
      returnedBakeStatus == completedBakeStatus
  }

  void 'create bake launches job and returns new status when prior bake is completed and failure'() {
    setup:
      def cloudProviderBakeHandlerRegistryMock = Mock(CloudProviderBakeHandlerRegistry)
      def cloudProviderBakeHandlerMock = Mock(CloudProviderBakeHandler)
      def bakeStoreMock = Mock(RedisBackedBakeStore)
      def jobExecutorMock = Mock(JobExecutor)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGE_NAME,
                                        base_os: "ubuntu",
                                        cloud_provider_type: BakeRequest.CloudProviderType.gce)
      def failedBakeStatus = new BakeStatus(id: EXISTING_JOB_ID,
                                            resource_id: EXISTING_JOB_ID,
                                            state: BakeStatus.State.CANCELED,
                                            result: BakeStatus.Result.FAILURE)
      def newBakeStatus = new BakeStatus(id: JOB_ID, resource_id: JOB_ID, state: BakeStatus.State.RUNNING)

      @Subject
      def bakeryController = new BakeryController(cloudProviderBakeHandlerRegistry: cloudProviderBakeHandlerRegistryMock,
                                                  bakeStore: bakeStoreMock,
                                                  jobExecutor: jobExecutorMock)

    when:
      def returnedBakeStatus = bakeryController.createBake(REGION, bakeRequest, null)

    then:
      1 * cloudProviderBakeHandlerRegistryMock.lookup(BakeRequest.CloudProviderType.gce) >> cloudProviderBakeHandlerMock
      1 * cloudProviderBakeHandlerMock.produceBakeKey(REGION, bakeRequest) >> BAKE_KEY
      1 * bakeStoreMock.retrieveBakeStatusByKey(BAKE_KEY) >> failedBakeStatus
      1 * cloudProviderBakeHandlerMock.producePackerCommand(REGION, bakeRequest) >> [PACKER_COMMAND]
      1 * bakeStoreMock.acquireBakeLock(BAKE_KEY) >> true
      1 * jobExecutorMock.startJob(new JobRequest(tokenizedCommand: [PACKER_COMMAND])) >> JOB_ID
      1 * jobExecutorMock.updateJob(JOB_ID) >> newBakeStatus
      1 * bakeStoreMock.storeNewBakeStatus(BAKE_KEY, REGION, bakeRequest, newBakeStatus, PACKER_COMMAND) >> newBakeStatus
      returnedBakeStatus == newBakeStatus
  }

  void 'create bake launches job and returns new status when prior bake is canceled'() {
    setup:
      def cloudProviderBakeHandlerRegistryMock = Mock(CloudProviderBakeHandlerRegistry)
      def cloudProviderBakeHandlerMock = Mock(CloudProviderBakeHandler)
      def bakeStoreMock = Mock(RedisBackedBakeStore)
      def jobExecutorMock = Mock(JobExecutor)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGE_NAME,
                                        base_os: "ubuntu",
                                        cloud_provider_type: BakeRequest.CloudProviderType.gce)
      def canceledBakeStatus = new BakeStatus(id: EXISTING_JOB_ID,
                                              resource_id: EXISTING_JOB_ID,
                                              state: BakeStatus.State.CANCELED)
      def newBakeStatus = new BakeStatus(id: JOB_ID, resource_id: JOB_ID, state: BakeStatus.State.RUNNING)

      @Subject
      def bakeryController = new BakeryController(cloudProviderBakeHandlerRegistry: cloudProviderBakeHandlerRegistryMock,
                                                  bakeStore: bakeStoreMock,
                                                  jobExecutor: jobExecutorMock)

    when:
      def returnedBakeStatus = bakeryController.createBake(REGION, bakeRequest, null)

    then:
      1 * cloudProviderBakeHandlerRegistryMock.lookup(BakeRequest.CloudProviderType.gce) >> cloudProviderBakeHandlerMock
      1 * cloudProviderBakeHandlerMock.produceBakeKey(REGION, bakeRequest) >> BAKE_KEY
      1 * bakeStoreMock.retrieveBakeStatusByKey(BAKE_KEY) >> canceledBakeStatus
      1 * cloudProviderBakeHandlerMock.producePackerCommand(REGION, bakeRequest) >> [PACKER_COMMAND]
      1 * bakeStoreMock.acquireBakeLock(BAKE_KEY) >> true
      1 * jobExecutorMock.startJob(new JobRequest(tokenizedCommand: [PACKER_COMMAND])) >> JOB_ID
      1 * jobExecutorMock.updateJob(JOB_ID) >> newBakeStatus
      1 * bakeStoreMock.storeNewBakeStatus(BAKE_KEY, REGION, bakeRequest, newBakeStatus, PACKER_COMMAND) >> newBakeStatus
      returnedBakeStatus == newBakeStatus
  }

  void 'create bake with rebake deletes existing status, launches job and returns new status no matter the pre-existing status'() {
    setup:
      def cloudProviderBakeHandlerRegistryMock = Mock(CloudProviderBakeHandlerRegistry)
      def cloudProviderBakeHandlerMock = Mock(CloudProviderBakeHandler)
      def bakeStoreMock = Mock(RedisBackedBakeStore)
      def jobExecutorMock = Mock(JobExecutor)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGE_NAME,
                                        base_os: "ubuntu",
                                        cloud_provider_type: BakeRequest.CloudProviderType.gce)
      def newBakeStatus = new BakeStatus(id: JOB_ID, resource_id: JOB_ID, state: BakeStatus.State.RUNNING)

      @Subject
      def bakeryController = new BakeryController(cloudProviderBakeHandlerRegistry: cloudProviderBakeHandlerRegistryMock,
                                                  bakeStore: bakeStoreMock,
                                                  jobExecutor: jobExecutorMock,
                                                  registry: new DefaultRegistry())

    when:
      def returnedBakeStatus = bakeryController.createBake(REGION, bakeRequest, "1")

    then:
      1 * cloudProviderBakeHandlerRegistryMock.lookup(BakeRequest.CloudProviderType.gce) >> cloudProviderBakeHandlerMock
      1 * cloudProviderBakeHandlerMock.produceBakeKey(REGION, bakeRequest) >> BAKE_KEY
      1 * bakeStoreMock.deleteBakeByKeyPreserveDetails(BAKE_KEY) >> BAKE_ID
      1 * cloudProviderBakeHandlerMock.producePackerCommand(REGION, bakeRequest) >> [PACKER_COMMAND]
      1 * bakeStoreMock.acquireBakeLock(BAKE_KEY) >> true
      1 * jobExecutorMock.startJob(new JobRequest(tokenizedCommand: [PACKER_COMMAND])) >> JOB_ID
      1 * jobExecutorMock.updateJob(JOB_ID) >> newBakeStatus
      1 * bakeStoreMock.storeNewBakeStatus(BAKE_KEY, REGION, bakeRequest, newBakeStatus, PACKER_COMMAND) >> newBakeStatus
      returnedBakeStatus == newBakeStatus
  }

  void 'lookup status queries bake store and returns bake status'() {
    setup:
      def cloudProviderBakeHandlerRegistryMock = Mock(CloudProviderBakeHandlerRegistry)
      def bakeStoreMock = Mock(RedisBackedBakeStore)
      def runningBakeStatus = new BakeStatus(id: JOB_ID,
                                             resource_id: JOB_ID,
                                             state: BakeStatus.State.RUNNING,
                                             result: null)

      @Subject
      def bakeryController = new BakeryController(cloudProviderBakeHandlerRegistry: cloudProviderBakeHandlerRegistryMock,
                                                  bakeStore: bakeStoreMock)

    when:
      def returnedBakeStatus = bakeryController.lookupStatus(REGION, JOB_ID)

    then:
      1 * bakeStoreMock.retrieveBakeStatusById(JOB_ID) >> runningBakeStatus
      returnedBakeStatus == runningBakeStatus
  }

  void 'lookup status throws exception when job cannot be found'() {
    setup:
      def bakeStoreMock = Mock(RedisBackedBakeStore)

      @Subject
      def bakeryController = new BakeryController(bakeStore: bakeStoreMock)

    when:
      bakeryController.lookupStatus(REGION, JOB_ID)

    then:
      1 * bakeStoreMock.retrieveBakeStatusById(JOB_ID) >> null
      IllegalArgumentException e = thrown()
      e.message == "Unable to retrieve status for '123'."
  }

  void 'lookup bake queries bake store and returns bake details'() {
    setup:
      def bakeStoreMock = Mock(RedisBackedBakeStore)

      @Subject
      def bakeryController = new BakeryController(bakeStore: bakeStoreMock)

    when:
      def bakeDetails = bakeryController.lookupBake(REGION, JOB_ID)

    then:
      1 * bakeStoreMock.retrieveBakeDetailsById(JOB_ID) >> new Bake(id: JOB_ID, ami: AMI_ID, image_name: IMAGE_NAME)
      bakeDetails == new Bake(id: JOB_ID, ami: AMI_ID, image_name: IMAGE_NAME)
  }

  void 'lookup bake throws exception when job cannot be found'() {
    setup:
      def bakeStoreMock = Mock(RedisBackedBakeStore)

    @Subject
      def bakeryController = new BakeryController(bakeStore: bakeStoreMock)

    when:
      bakeryController.lookupBake(REGION, JOB_ID)

    then:
      1 * bakeStoreMock.retrieveBakeDetailsById(JOB_ID) >> null
      IllegalArgumentException e = thrown()
      e.message == "Unable to retrieve bake details for '123'."
  }

  void 'lookup logs queries bake store and returns logs content'() {
    setup:
      def bakeStoreMock = Mock(RedisBackedBakeStore)

      @Subject
      def bakeryController = new BakeryController(bakeStore: bakeStoreMock)

    when:
      def response = bakeryController.lookupLogs(REGION, JOB_ID, false)

    then:
      1 * bakeStoreMock.retrieveBakeLogsById(JOB_ID) >> [logsContent: LOGS_CONTENT]
      response == LOGS_CONTENT
  }

  void 'lookup logs throws exception when job logs are empty or malformed'() {
    setup:
      def bakeStoreMock = Mock(RedisBackedBakeStore)

    @Subject
      def bakeryController = new BakeryController(bakeStore: bakeStoreMock)

    when:
      bakeryController.lookupLogs(REGION, JOB_ID, false)

    then:
      1 * bakeStoreMock.retrieveBakeLogsById(JOB_ID) >> null
      IllegalArgumentException e = thrown()
      e.message == "Unable to retrieve logs for '123'."

    when:
      bakeryController.lookupLogs(REGION, JOB_ID, false)

    then:
      1 * bakeStoreMock.retrieveBakeLogsById(JOB_ID) >> [:]
      e = thrown()
      e.message == "Unable to retrieve logs for '123'."

    when:
      bakeryController.lookupLogs(REGION, JOB_ID, false)

    then:
      1 * bakeStoreMock.retrieveBakeLogsById(JOB_ID) >> [logsContent: null]
      e = thrown()
      e.message == "Unable to retrieve logs for '123'."

    when:
      bakeryController.lookupLogs(REGION, JOB_ID, false)

    then:
      1 * bakeStoreMock.retrieveBakeLogsById(JOB_ID) >> [logsContent: '']
      e = thrown()
      e.message == "Unable to retrieve logs for '123'."
  }

  void 'delete bake updates bake store and returns status'() {
    setup:
      def cloudProviderBakeHandlerRegistryMock = Mock(CloudProviderBakeHandlerRegistry)
      def cloudProviderBakeHandlerMock = Mock(CloudProviderBakeHandler)
      def bakeStoreMock = Mock(RedisBackedBakeStore)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGE_NAME,
                                        base_os: "ubuntu",
                                        cloud_provider_type: BakeRequest.CloudProviderType.gce)

      @Subject
      def bakeryController = new BakeryController(cloudProviderBakeHandlerRegistry: cloudProviderBakeHandlerRegistryMock,
                                                  bakeStore: bakeStoreMock)

    when:
      def response = bakeryController.deleteBake(REGION, bakeRequest)

    then:
      1 * cloudProviderBakeHandlerRegistryMock.lookup(BakeRequest.CloudProviderType.gce) >> cloudProviderBakeHandlerMock
      1 * cloudProviderBakeHandlerMock.produceBakeKey(REGION, bakeRequest) >> BAKE_KEY
      1 * bakeStoreMock.deleteBakeByKey(BAKE_KEY) >> BAKE_ID
      response == "Deleted bake '$BAKE_KEY' with id '$BAKE_ID'."
  }

  void 'delete bake throws exception when bake key cannot be found'() {
    setup:
      def cloudProviderBakeHandlerRegistryMock = Mock(CloudProviderBakeHandlerRegistry)
      def cloudProviderBakeHandlerMock = Mock(CloudProviderBakeHandler)
      def bakeStoreMock = Mock(RedisBackedBakeStore)
      def bakeRequest = new BakeRequest(user: "someuser@gmail.com",
                                        package_name: PACKAGE_NAME,
                                        base_os: "ubuntu",
                                        cloud_provider_type: BakeRequest.CloudProviderType.gce)

      @Subject
      def bakeryController = new BakeryController(cloudProviderBakeHandlerRegistry: cloudProviderBakeHandlerRegistryMock,
                                                  bakeStore: bakeStoreMock)

    when:
      bakeryController.deleteBake(REGION, bakeRequest)

    then:
      1 * cloudProviderBakeHandlerRegistryMock.lookup(BakeRequest.CloudProviderType.gce) >> cloudProviderBakeHandlerMock
      1 * cloudProviderBakeHandlerMock.produceBakeKey(REGION, bakeRequest) >> BAKE_KEY
      1 * bakeStoreMock.deleteBakeByKey(BAKE_KEY) >> null
      IllegalArgumentException e = thrown()
      e.message == "Unable to locate bake with key '$BAKE_KEY'."
  }

  void 'cancel bake updates bake store, kills the job and returns status'() {
    setup:
      def bakeStoreMock = Mock(RedisBackedBakeStore)
      def jobExecutorMock = Mock(JobExecutor)

    @Subject
      def bakeryController = new BakeryController(bakeStore: bakeStoreMock,
                                                  jobExecutor: jobExecutorMock,
                                                  registry: new DefaultRegistry())

    when:
      def response = bakeryController.cancelBake(REGION, JOB_ID)

    then:
      1 * bakeStoreMock.cancelBakeById(JOB_ID) >> true
      1 * jobExecutorMock.cancelJob(JOB_ID)
      1 * bakeStoreMock.retrieveBakeStatusById(JOB_ID) >> new BakeStatus()
      response == "Canceled bake '$JOB_ID'."
  }

  void 'cancel bake throws exception when bake id cannot be found'() {
    setup:
      def bakeStoreMock = Mock(RedisBackedBakeStore)

      @Subject
      def bakeryController = new BakeryController(bakeStore: bakeStoreMock)

    when:
      bakeryController.cancelBake(REGION, JOB_ID)

    then:
      1 * bakeStoreMock.cancelBakeById(JOB_ID) >> false
      IllegalArgumentException e = thrown()
      e.message == "Unable to locate incomplete bake with id '$JOB_ID'."
  }

  def "should list bake options by cloud provider"() {
    setup:
      def provider1 = Mock(CloudProviderBakeHandler) {
        2 * getBakeOptions() >> new BakeOptions(cloudProvider: "aws", baseImages: [new BakeOptions.BaseImage(id: "santa")])
      }
      def provider2 = Mock(CloudProviderBakeHandler) {
        2 * getBakeOptions() >> new BakeOptions(cloudProvider: "gce", baseImages: [new BakeOptions.BaseImage(id: "claus")])
      }
      def provider3 = Mock(CloudProviderBakeHandler) {
        2 * getBakeOptions() >> new BakeOptions(cloudProvider: "openstack", baseImages: [new BakeOptions.BaseImage(id: "misses")])
      }

      def registry = new DefaultCloudProviderBakeHandlerRegistry()
      registry.with {
        register(BakeRequest.CloudProviderType.aws, provider1)
        register(BakeRequest.CloudProviderType.gce, provider2)
        register(BakeRequest.CloudProviderType.openstack, provider3)
      }
      def bakeryController = new BakeryController(cloudProviderBakeHandlerRegistry: registry)

    when:
      def result = bakeryController.bakeOptions()

    then:
      result.size == 3
      result.find { it.cloudProvider == "aws" }.baseImages[0].id == "santa"
      result.find { it.cloudProvider == "gce" }.baseImages[0].id == "claus"
      result.find { it.cloudProvider == "openstack" }.baseImages[0].id == "misses"

    when:
      result = bakeryController.bakeOptionsByCloudProvider(BakeRequest.CloudProviderType.aws)

    then:
      result
      result.cloudProvider == "aws"
      result.baseImages[0].id == "santa"


    when:
      result = bakeryController.bakeOptionsByCloudProvider(BakeRequest.CloudProviderType.gce)

    then:
      result
      result.cloudProvider == "gce"
      result.baseImages[0].id == "claus"

    when:
      result = bakeryController.bakeOptionsByCloudProvider(BakeRequest.CloudProviderType.openstack)

    then:
      result
      result.cloudProvider == "openstack"
      result.baseImages[0].id == "misses"

    when:
      bakeryController.bakeOptionsByCloudProvider(BakeRequest.CloudProviderType.docker)

    then:
      thrown BakeOptions.Exception
  }

  def "should return base image details"() {
    setup:
      def provider = Mock(CloudProviderBakeHandler) {
        3 * getBakeOptions() >> new BakeOptions(cloudProvider: "gce",
                                                baseImages: [
                                                  new BakeOptions.BaseImage(id: "santa", shortDescription: "abc"),
                                                  new BakeOptions.BaseImage(id: "clause", shortDescription: "def")
                                                ])
      }

      def registry = new DefaultCloudProviderBakeHandlerRegistry()
      registry.with {
        register(BakeRequest.CloudProviderType.gce, provider)
      }
      def bakeryController = new BakeryController(cloudProviderBakeHandlerRegistry: registry)

    when:
      def result = bakeryController.baseImage(BakeRequest.CloudProviderType.gce, "santa")

    then:
      result.shortDescription == "abc"

    when:
      result = bakeryController.baseImage(BakeRequest.CloudProviderType.gce, "clause")

    then:
      result.shortDescription == "def"

    when:
      bakeryController.baseImage(BakeRequest.CloudProviderType.gce, "notFound")

    then:
      thrown BakeOptions.Exception
  }

}
