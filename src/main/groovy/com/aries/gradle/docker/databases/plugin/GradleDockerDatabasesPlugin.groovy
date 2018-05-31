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
import org.gradle.api.Task

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
    private createTaskChain_Up(final Project project, final String taskNamePrefix, final String taskGroup, final def taskGroupExtension) {

        final Task availableDataContainerTask = project.task("${taskNamePrefix}AvailableDataContainer",
            type: com.bmuschko.gradle.docker.tasks.container.DockerInspectContainer) {

            group: taskGroup
            description: 'Check if data container is available.'

            targetContainerId { taskGroupExtension.databaseDataId() }

            ext.exists = true
            onNext {} // defining so that the output will get piped to nowhere as we don't need it
            onError { err ->
                if (!err.class.simpleName.matches(NOT_PRESENT_REGEX)) {
                    throw err
                } else {
                    ext.exists = false
                    logger.quiet "Container with ID '${taskGroupExtension.databaseDataId()}' is not available to inspect."
                }
            }
        }

        final Task availableContainerTask = project.task("${taskNamePrefix}AvailableContainer",
            type: com.bmuschko.gradle.docker.tasks.container.DockerInspectContainer,
            dependsOn: [availableDataContainerTask]) {

            group: taskGroup
            description: 'Check if container is available.'

            targetContainerId { taskGroupExtension.databaseId() }

            ext.exists = true
            ext.inspection = null
            onNext { possibleContainer ->
                ext.inspection = possibleContainer
            }
            onError { err ->
                if (!err.class.simpleName.matches(NOT_PRESENT_REGEX)) {
                    throw err
                } else {
                    ext.exists = false
                    logger.quiet "Container with ID '${taskGroupExtension.databaseId()}' is not available to inspect."
                }
            }
        }

        final Task restartContainerTask = project.task("${taskNamePrefix}RestartContainer",
            type: com.bmuschko.gradle.docker.tasks.container.DockerRestartContainer,
            dependsOn: [availableContainerTask]) {
            onlyIf { availableContainerTask.ext.exists == true &&
                availableContainerTask.ext.inspection.state.running == false }

            group: taskGroup
            description: 'Restart container if it is present and not running.'

            doFirst {
                ext.startTime = new Date()
            }
            targetContainerId { taskGroupExtension.databaseId() }
            timeout = 30000
        }

        // if a previous main/data container is present than the assumption is that
        // the image in question must also be present and so we don't need to check
        // for the existence of its backing image
        final Task listImagesTask = project.task("${taskNamePrefix}ListImages",
            type: com.bmuschko.gradle.docker.tasks.image.DockerListImages,
            dependsOn: [restartContainerTask]) {
            onlyIf { availableDataContainerTask.ext.exists == false &&
                availableContainerTask.ext.exists == false }

            group: taskGroup
            description: 'Check if image exists locally.'

            imageName = taskGroupExtension.repository()

            // check if the image we need is already available so that we don't
            // have to pull it further below
            ext.imageAvailableLocally = false
            onNext { possibleImage ->
                if (ext.imageAvailableLocally == false) {
                    possibleImage.repoTags.each { rep ->
                        if (ext.imageAvailableLocally == false && rep.first() == taskGroupExtension.image()) {
                            logger.quiet "Image with ID '${taskGroupExtension.image()}' was found locally: pull not required."
                            ext.imageAvailableLocally = true
                        }
                    }
                }
            }
        }

        final Task pullImageTask = project.task("${taskNamePrefix}PullImage",
            type: com.bmuschko.gradle.docker.tasks.image.DockerPullImage,
            dependsOn: [listImagesTask]) {
            onlyIf { (availableDataContainerTask.ext.exists == false &&
                availableContainerTask.ext.exists == false) &&
                listImagesTask.ext.imageAvailableLocally == false }

            group: taskGroup
            description: 'Pull image.'

            repository = taskGroupExtension.repository()
            tag = taskGroupExtension.tag()
        }

        final Task removeContainerTask = project.task("${taskNamePrefix}RemoveContainer",
            type: com.bmuschko.gradle.docker.tasks.container.DockerRemoveContainer,
            dependsOn: [pullImageTask]) {
            onlyIf { availableContainerTask.ext.exists == true &&
                availableContainerTask.ext.inspection.state.running == false &&
                restartContainerTask.state.didWork == false }

            group: taskGroup
            description: 'Remove container.'

            removeVolumes = true
            force = true
            targetContainerId { taskGroupExtension.databaseId() }

            onError { err ->
                if (!err.class.simpleName.matches(NOT_PRESENT_REGEX)) {
                    throw err
                } else {
                    logger.quiet "Container with ID '${taskGroupExtension.databaseId()}' is not available to remove."
                }
            }
        }

        final Task removeDataContainerTask = project.task("${taskNamePrefix}RemoveDataContainer",
            type: com.bmuschko.gradle.docker.tasks.container.DockerRemoveContainer,
            dependsOn: [removeContainerTask]) {
            onlyIf { availableDataContainerTask.ext.exists == true &&
                restartContainerTask.state.didWork == false &&
                removeContainerTask.state.didWork == true }

            group: taskGroup
            description: 'Remove data container.'

            removeVolumes = true
            force = true
            targetContainerId { taskGroupExtension.databaseDataId() }

            onError { err ->
                if (!err.class.simpleName.matches(NOT_PRESENT_REGEX)) {
                    throw err
                } else {
                    logger.quiet "Container with ID '${taskGroupExtension.databaseDataId()}' is not available to remove."
                }
            }
        }

        final Task createDataContainerTask = project.task("${taskNamePrefix}CreateDataContainer",
            type: com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer,
            dependsOn: [removeDataContainerTask]) {
            onlyIf { availableDataContainerTask.ext.exists == false }

            group: taskGroup
            description: 'Create data container.'

            targetImageId { taskGroupExtension.image() }
            containerName = taskGroupExtension.databaseDataId()
            volumes = ["/var/lib/postgresql/data"]
        }

        final Task createContainerTask = project.task("${taskNamePrefix}CreateContainer",
            type: com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer,
            dependsOn: [createDataContainerTask]) {
            onlyIf { availableContainerTask.ext.exists == false }

            group: taskGroup
            description: 'Create container.'

            targetImageId { taskGroupExtension.image() }
            containerName = taskGroupExtension.databaseId()
            volumesFrom = [taskGroupExtension.databaseDataId()]
        }

        final Task startContainerTask = project.task("${taskNamePrefix}StartContainer",
            type: com.bmuschko.gradle.docker.tasks.container.DockerStartContainer,
            dependsOn: [createContainerTask]) {
            onlyIf { createContainerTask.state.didWork }

            group: taskGroup
            description: 'Start container.'

            doFirst {
                ext.startTime = new Date()
            }
            targetContainerId { taskGroupExtension.databaseId() }
        }

        final Task livenessProbeContainerTask = project.task("${taskNamePrefix}LivenessProbeContainer",
            type: DockerLivenessProbeContainer,
            dependsOn: [startContainerTask]) {
            onlyIf { startContainerTask.state.didWork ||
                restartContainerTask.state.didWork }

            group: taskGroup
            description: 'Check if container is live.'

            targetContainerId { taskGroupExtension.databaseId() }

            // only 2 ways this task can kick so we will proceed to configure
            // it based upon which one did actual work
            doFirst {
                since = startContainerTask.state.didWork ?
                    startContainerTask.ext.startTime :
                    restartContainerTask.ext.startTime
            }
            tailCount = 10
            showTimestamps = true
            probe(300000, 30000, taskGroupExtension.liveOnLog())
        }

        project.task("${taskNamePrefix}Up",
            dependsOn: [livenessProbeContainerTask]) {
            group: taskGroup
            description: 'Start container stack if not already started.'
        }
    }

    // create required tasks for invoking the "stop" chain.
    private createTaskChain_Stop(final Project project, final String taskNamePrefix, final String taskGroup, final def taskGroupExtension) {

        final Task stopContainerTask = project.task("${taskNamePrefix}StopContainer",
            type: com.bmuschko.gradle.docker.tasks.container.DockerStopContainer) {

            group: taskGroup
            description: 'Stop container.'

            timeout = 30000
            targetContainerId { taskGroupExtension.databaseId() }

            onError { err ->
                if (!err.class.simpleName.matches(NOT_PRESENT_REGEX)) {
                    throw err
                } else {
                    logger.quiet "Container with ID '${taskGroupExtension.databaseId()}' is not available to stop."
                }
            }
        }

        project.task("${taskNamePrefix}Stop",
            dependsOn: [stopContainerTask]) {
            group: taskGroup
            description: 'Stop container stack if not already stopped.'
        }
    }

    // create required tasks for invoking the "down" chain.
    private createTaskChain_Down(final Project project, final String taskNamePrefix, final String taskGroup, final def taskGroupExtension) {

        final Task deleteContainerTask = project.task("${taskNamePrefix}DeleteContainer",
            type: com.bmuschko.gradle.docker.tasks.container.DockerRemoveContainer) {

            group: taskGroup
            description: 'Delete container.'

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

        final Task deleteDataContainerTask = project.task("${taskNamePrefix}DeleteDataContainer",
            type: com.bmuschko.gradle.docker.tasks.container.DockerRemoveContainer,
            dependsOn: [deleteContainerTask]) {

            group: taskGroup
            description: 'Delete data container.'

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

        project.task("${taskNamePrefix}Down",
            dependsOn: [deleteDataContainerTask]) {

            group: taskGroup
            description: 'Delete container stack if not already deleted.'
        }
    }
}
