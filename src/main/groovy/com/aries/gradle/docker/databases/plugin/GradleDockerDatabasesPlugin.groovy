/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.aries.gradle.docker.databases.plugin

import com.aries.gradle.docker.databases.plugin.tasks.DockerLivenessProbeContainer

import org.gradle.api.Plugin
import org.gradle.api.Project
import com.aries.gradle.docker.databases.plugin.extensions.Databases
import com.aries.gradle.docker.databases.plugin.extensions.Db2
import com.aries.gradle.docker.databases.plugin.extensions.Oracle
import com.aries.gradle.docker.databases.plugin.extensions.Postgres
import com.aries.gradle.docker.databases.plugin.extensions.Sqlserver

/**
 *  Plugin providing common tasks for interacting with various dockerized databases.
 */
class GradleDockerDatabasesPlugin implements Plugin<Project> {

    public static final List<Class> DATABASES = [Db2, Oracle, Postgres, Sqlserver].asImmutable()

    public static final String NOT_PRESENT_REGEX = '^(NotModifiedException|NotFoundException)$'

    @Override
    void apply(final Project project) {

        // 1.) apply required plugins
        project.plugins.apply('com.bmuschko.docker-remote-api')

        // 2.) create all database extension points
        createDatabaseExtensionPoints(project)

        // 3.) create all database tasks
        createDatabaseTasks(project)
    }

    /*
     * Create our various database extension points.
     */
    private createDatabaseExtensionPoints(final Project project) {
        project.extensions.create(Databases.simpleName.toLowerCase(), Databases)
        DATABASES.each { dbClass ->
            project.extensions.create(dbClass.simpleName.toLowerCase(), dbClass)
        }
    }

    /*
     *  Create common tasks for all databases
     */
    private createDatabaseTasks(final Project project) {
        DATABASES.each { dbClass ->

            // commmon variables used by all tasks below
            final String dbType = dbClass.simpleName
            final String dbGroup = "${dbType}-database"
            final def dbExtension = project.extensions.getByName(dbType.toLowerCase())

            // create tasks after evaluation so that we can pick up any changes
            // made to our various extension points.
            project.afterEvaluate {
                createTaskChain_Up(project, dbType, dbGroup, dbExtension)
                createTaskChain_Stop(project, dbType, dbGroup, dbExtension)
                createTaskChain_Down(project, dbType, dbGroup, dbExtension)
            }
        }
    }

    // create required tasks for invoking the "up" chain.
    private createTaskChain_Up(final Project project, final String dbType, final String dbGroup, final def dbExtension) {

        final def availableDataContainerTaskName = "${dbType}AvailableDataContainer"
        project.task(availableDataContainerTaskName,
            type: com.bmuschko.gradle.docker.tasks.container.DockerInspectContainer) {

            group: dbGroup
            description: 'Check if data container is available.'

            targetContainerId { dbExtension.databaseDataId() }

            ext.exists = true
            onNext {} // defining so that the output will get piped to nowhere as we don't need it
            onError { err ->
                if (!err.class.simpleName.matches(NOT_PRESENT_REGEX)) {
                    throw err
                } else {
                    ext.exists = false
                    logger.quiet "Container with ID '${dbExtension.databaseDataId()}' is not available to inspect."
                }
            }
        }

        final def availableContainerTaskName = "${dbType}AvailableContainer"
        project.task(availableContainerTaskName,
            type: com.bmuschko.gradle.docker.tasks.container.DockerInspectContainer,
            dependsOn: [availableDataContainerTaskName]) {

            group: dbGroup
            description: 'Check if container is available and possibly running.'

            targetContainerId { dbExtension.databaseId() }

            ext.exists = true
            ext.running = false
            onNext { possibleContainer ->
                ext.running = possibleContainer.getState().getRunning()
            }
            onError { err ->
                if (!err.class.simpleName.matches(NOT_PRESENT_REGEX)) {
                    throw err
                } else {
                    ext.exists = false
                    logger.quiet "Container with ID '${dbExtension.databaseId()}' is not available to inspect."
                }
            }
        }

        final def restartContainerTaskName = "${dbType}RestartContainer"
        project.task(restartContainerTaskName,
            type: com.bmuschko.gradle.docker.tasks.container.DockerRestartContainer,
            dependsOn: [availableContainerTaskName]) {
            onlyIf { project.tasks.getByName(availableContainerTaskName).ext.exists == true &&
                project.tasks.getByName(availableContainerTaskName).ext.running == false }

            group: dbGroup
            description: 'Restart container if it is present and not running.'

            targetContainerId { dbExtension.databaseId() }
            timeout = 30000
        }

        final def inspectContainerTaskName = "${dbType}InspectContainer"
        project.task(inspectContainerTaskName,
            type: com.bmuschko.gradle.docker.tasks.container.DockerInspectContainer,
            dependsOn: [restartContainerTaskName]) {
            onlyIf { project.tasks.getByName(restartContainerTaskName).state.didWork }

            group: dbGroup
            description: 'Check if container is available and still running after restart.'

            targetContainerId { dbExtension.databaseId() }

            ext.exists = false
            ext.running = false
            onNext { possibleContainer ->
                ext.exists = true
                ext.running = possibleContainer.getState().getRunning()
            }
        }

        // if a previous main/data container is present than the assumption is that
        // the image in question must also be present and so we don't need to check
        // for the existence of its backing image
        final def listImagesTaskName = "${dbType}ListImages"
        project.task(listImagesTaskName,
            type: com.bmuschko.gradle.docker.tasks.image.DockerListImages,
            dependsOn: [inspectContainerTaskName]) {
            onlyIf { project.tasks.getByName(availableDataContainerTaskName).ext.exists == false &&
                project.tasks.getByName(availableContainerTaskName).ext.exists == false }

            group: dbGroup
            description: 'Check if database image exists locally.'

            imageName = dbExtension.repository()

            // check if the image we need is already available so that we don't
            // have to pull it further below
            ext.imageAvailableLocally = false
            onNext { possibleImage ->
                if (ext.imageAvailableLocally == false) {
                    possibleImage.repoTags.each { rep ->
                        if (ext.imageAvailableLocally == false && rep.first() == dbExtension.image()) {
                            logger.quiet "Image with ID '${dbExtension.image()}' was found locally: pull not required."
                            ext.imageAvailableLocally = true
                        }
                    }
                }
            }
        }

        final def pullImageTaskName = "${dbType}PullImage"
        project.task(pullImageTaskName,
            type: com.bmuschko.gradle.docker.tasks.image.DockerPullImage,
            dependsOn: [listImagesTaskName]) {
            onlyIf { (project.tasks.getByName(availableDataContainerTaskName).ext.exists == false &&
                project.tasks.getByName(availableContainerTaskName).ext.exists == false) &&
                project.tasks.getByName(listImagesTaskName).ext.imageAvailableLocally == false }

            group: dbGroup
            description: 'Pull database image.'

            repository = dbExtension.repository()
            tag = dbExtension.tag()
        }

        final def removeContainerTaskName = "${dbType}RemoveContainer"
        project.task(removeContainerTaskName,
            type: com.bmuschko.gradle.docker.tasks.container.DockerRemoveContainer,
            dependsOn: [pullImageTaskName]) {
            onlyIf { project.tasks.getByName(availableContainerTaskName).ext.exists == true &&
                project.tasks.getByName(availableContainerTaskName).ext.running == false &&
                project.tasks.getByName(restartContainerTaskName).state.didWork == false }

            group: dbGroup
            description: 'Remove database container.'

            removeVolumes = true
            force = true
            targetContainerId { dbExtension.databaseId() }

            onError { err ->
                if (!err.class.simpleName.matches(NOT_PRESENT_REGEX)) {
                    throw err
                } else {
                    logger.quiet "Container with ID '${dbExtension.databaseId()}' is not available to remove."
                }
            }
        }

        final def removeDataContainerTaskName = "${dbType}RemoveDataContainer"
        project.task(removeDataContainerTaskName,
            type: com.bmuschko.gradle.docker.tasks.container.DockerRemoveContainer,
            dependsOn: [removeContainerTaskName]) {
            onlyIf { project.tasks.getByName(availableDataContainerTaskName).ext.exists == true &&
                project.tasks.getByName(restartContainerTaskName).state.didWork == false &&
                project.tasks.getByName(removeContainerTaskName).state.didWork == true }

            group: dbGroup
            description: 'Remove database data container.'

            removeVolumes = true
            force = true
            targetContainerId { dbExtension.databaseDataId() }

            onError { err ->
                if (!err.class.simpleName.matches(NOT_PRESENT_REGEX)) {
                    throw err
                } else {
                    logger.quiet "Container with ID '${dbExtension.databaseDataId()}' is not available to remove."
                }
            }
        }

        final def createDataContainerTaskName = "${dbType}CreateDataContainer"
        project.task(createDataContainerTaskName,
            type: com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer,
            dependsOn: [removeDataContainerTaskName]) {
            onlyIf { project.tasks.getByName(availableDataContainerTaskName).ext.exists == false }

            group: dbGroup
            description: 'Create database data container.'

            targetImageId { dbExtension.image() }
            containerName = dbExtension.databaseDataId()
            volumes = ["/var/lib/postgresql/data"]
        }

        final def createContainerTaskName = "${dbType}CreateContainer"
        project.task(createContainerTaskName,
            type: com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer,
            dependsOn: [createDataContainerTaskName]) {
            onlyIf { project.tasks.getByName(availableContainerTaskName).ext.exists == false }

            group: dbGroup
            description: 'Create database container.'

            targetImageId { dbExtension.image() }
            containerName = dbExtension.databaseId()
            volumesFrom = [dbExtension.databaseDataId()]
        }

        final def startContainerTaskName = "${dbType}StartContainer"
        project.task(startContainerTaskName,
            type: com.bmuschko.gradle.docker.tasks.container.DockerStartContainer,
            dependsOn: [createContainerTaskName]) {
            onlyIf { project.tasks.getByName(createContainerTaskName).state.didWork }

            group: dbGroup
            description: 'Start database container.'

            doFirst {
                ext.startTime = new Date()
            }
            targetContainerId { dbExtension.databaseId() }
        }

        final def livenessProbeContainerTaskName = "${dbType}LivenessProbeContainer"
        project.task(livenessProbeContainerTaskName,
            type: DockerLivenessProbeContainer,
            dependsOn: [startContainerTaskName]) {
            onlyIf { project.tasks.getByName(startContainerTaskName).state.didWork }

            group: dbGroup
            description: 'Check if database container is live.'

            targetContainerId { dbExtension.databaseId() }

            doFirst {
                since = project.tasks.getByName(startContainerTaskName).ext.startTime
            }
            tailCount = 10
            showTimestamps = true
            probe(300000, 30000, dbExtension.liveOnLog())
        }

        final def upTaskName = "${dbType}Up"
        project.task(upTaskName,
            dependsOn: [livenessProbeContainerTaskName]) {
            group: dbGroup
            description: 'Start database container stack if not already started.'
        }
    }

    // create required tasks for invoking the "stop" chain.
    private createTaskChain_Stop(final Project project, final String dbType, final String dbGroup, final def dbExtension) {

        final def stopContainerTaskName = "${dbType}StopContainer"
        project.task(stopContainerTaskName,
            type: com.bmuschko.gradle.docker.tasks.container.DockerStopContainer) {

            group: dbGroup
            description: 'Stop database container.'

            timeout = 30000
            targetContainerId { dbExtension.databaseId() }

            onError { err ->
                if (!err.class.simpleName.matches(NOT_PRESENT_REGEX)) {
                    throw err
                } else {
                    logger.quiet "Container with ID '${dbExtension.databaseId()}' is not available to stop."
                }
            }
        }

        final def stopTaskName = "${dbType}Stop"
        project.task(stopTaskName,
            dependsOn: [stopContainerTaskName]) {
            group: dbGroup
            description: 'Stop database container stack if not already stopped.'
        }
    }

    // create required tasks for invoking the "down" chain.
    private createTaskChain_Down(final Project project, final String dbType, final String dbGroup, final def dbExtension) {

        final def deleteContainerTaskName = "${dbType}DeleteContainer"
        project.task(deleteContainerTaskName,
            type: com.bmuschko.gradle.docker.tasks.container.DockerRemoveContainer) {

            group: dbGroup
            description: 'Delete database container.'

            removeVolumes = true
            force = true
            targetContainerId { dbExtension.databaseId() }

            onError { err ->
                if (!err.class.simpleName.matches(NOT_PRESENT_REGEX)) {
                    throw err
                } else {
                    logger.quiet "Container with ID '${dbExtension.databaseId()}' is not available to delete."
                }
            }
        }

        final def deleteDataContainerTaskName = "${dbType}DeleteDataContainer"
        project.task(deleteDataContainerTaskName,
            type: com.bmuschko.gradle.docker.tasks.container.DockerRemoveContainer,
            dependsOn: [deleteContainerTaskName]) {

            group: dbGroup
            description: 'Delete database data container.'

            removeVolumes = true
            force = true
            targetContainerId { dbExtension.databaseDataId() }

            onError { err ->
                if (!err.class.simpleName.matches(NOT_PRESENT_REGEX)) {
                    throw err
                } else {
                    logger.quiet "Container with ID '${dbExtension.databaseDataId()}' is not available to delete."
                }
            }
        }

        final def downTaskName = "${dbType}Down"
        project.task(downTaskName,
            dependsOn: [deleteDataContainerTaskName]) {

            group: dbGroup
            description: 'Delete database container stack if not already deleted.'
        }
    }
}
