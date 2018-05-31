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
    private createTaskChain_Up(final Project project, final String dbType, final String dbGroup, final def dbExtension) {

        final Task availableDataContainerTask = project.task("${dbType}AvailableDataContainer",
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

        final Task availableContainerTask = project.task("${dbType}AvailableContainer",
            type: com.bmuschko.gradle.docker.tasks.container.DockerInspectContainer,
            dependsOn: [availableDataContainerTask]) {

            group: dbGroup
            description: 'Check if container is available.'

            targetContainerId { dbExtension.databaseId() }

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
                    logger.quiet "Container with ID '${dbExtension.databaseId()}' is not available to inspect."
                }
            }
        }

        final Task restartContainerTask = project.task("${dbType}RestartContainer",
            type: com.bmuschko.gradle.docker.tasks.container.DockerRestartContainer,
            dependsOn: [availableContainerTask]) {
            onlyIf { availableContainerTask.ext.exists == true &&
                availableContainerTask.ext.inspection.state.running == false }

            group: dbGroup
            description: 'Restart container if it is present and not running.'

            doFirst {
                ext.startTime = new Date()
            }
            targetContainerId { dbExtension.databaseId() }
            timeout = 30000
        }

        // if a previous main/data container is present than the assumption is that
        // the image in question must also be present and so we don't need to check
        // for the existence of its backing image
        final Task listImagesTask = project.task("${dbType}ListImages",
            type: com.bmuschko.gradle.docker.tasks.image.DockerListImages,
            dependsOn: [restartContainerTask]) {
            onlyIf { availableDataContainerTask.ext.exists == false &&
                availableContainerTask.ext.exists == false }

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

        final Task pullImageTask = project.task("${dbType}PullImage",
            type: com.bmuschko.gradle.docker.tasks.image.DockerPullImage,
            dependsOn: [listImagesTask]) {
            onlyIf { (availableDataContainerTask.ext.exists == false &&
                availableContainerTask.ext.exists == false) &&
                listImagesTask.ext.imageAvailableLocally == false }

            group: dbGroup
            description: 'Pull database image.'

            repository = dbExtension.repository()
            tag = dbExtension.tag()
        }

        final Task removeContainerTask = project.task("${dbType}RemoveContainer",
            type: com.bmuschko.gradle.docker.tasks.container.DockerRemoveContainer,
            dependsOn: [pullImageTask]) {
            onlyIf { availableContainerTask.ext.exists == true &&
                availableContainerTask.ext.inspection.state.running == false &&
                restartContainerTask.state.didWork == false }

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

        final Task removeDataContainerTask = project.task("${dbType}RemoveDataContainer",
            type: com.bmuschko.gradle.docker.tasks.container.DockerRemoveContainer,
            dependsOn: [removeContainerTask]) {
            onlyIf { availableDataContainerTask.ext.exists == true &&
                restartContainerTask.state.didWork == false &&
                removeContainerTask.state.didWork == true }

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

        final Task createDataContainerTask = project.task("${dbType}CreateDataContainer",
            type: com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer,
            dependsOn: [removeDataContainerTask]) {
            onlyIf { availableDataContainerTask.ext.exists == false }

            group: dbGroup
            description: 'Create database data container.'

            targetImageId { dbExtension.image() }
            containerName = dbExtension.databaseDataId()
            volumes = ["/var/lib/postgresql/data"]
        }

        final Task createContainerTask = project.task("${dbType}CreateContainer",
            type: com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer,
            dependsOn: [createDataContainerTask]) {
            onlyIf { availableContainerTask.ext.exists == false }

            group: dbGroup
            description: 'Create database container.'

            targetImageId { dbExtension.image() }
            containerName = dbExtension.databaseId()
            volumesFrom = [dbExtension.databaseDataId()]
        }

        final Task startContainerTask = project.task("${dbType}StartContainer",
            type: com.bmuschko.gradle.docker.tasks.container.DockerStartContainer,
            dependsOn: [createContainerTask]) {
            onlyIf { createContainerTask.state.didWork }

            group: dbGroup
            description: 'Start database container.'

            doFirst {
                ext.startTime = new Date()
            }
            targetContainerId { dbExtension.databaseId() }
        }

        final Task livenessProbeContainerTask = project.task("${dbType}LivenessProbeContainer",
            type: DockerLivenessProbeContainer,
            dependsOn: [startContainerTask]) {
            onlyIf { startContainerTask.state.didWork ||
                restartContainerTask.state.didWork }

            group: dbGroup
            description: 'Check if database container is live.'

            targetContainerId { dbExtension.databaseId() }

            // only 2 ways this task can kick so we will proceed to configure
            // it based upon which one did actual work
            doFirst {
                since = startContainerTask.state.didWork ?
                    startContainerTask.ext.startTime :
                    restartContainerTask.ext.startTime
            }
            tailCount = 10
            showTimestamps = true
            probe(300000, 30000, dbExtension.liveOnLog())
        }

        project.task("${dbType}Up",
            dependsOn: [livenessProbeContainerTask]) {
            group: dbGroup
            description: 'Start database container stack if not already started.'
        }
    }

    // create required tasks for invoking the "stop" chain.
    private createTaskChain_Stop(final Project project, final String dbType, final String dbGroup, final def dbExtension) {

        final Task stopContainerTask = project.task("${dbType}StopContainer",
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

        project.task("${dbType}Stop",
            dependsOn: [stopContainerTask]) {
            group: dbGroup
            description: 'Stop database container stack if not already stopped.'
        }
    }

    // create required tasks for invoking the "down" chain.
    private createTaskChain_Down(final Project project, final String dbType, final String dbGroup, final def dbExtension) {

        final Task deleteContainerTask = project.task("${dbType}DeleteContainer",
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

        final Task deleteDataContainerTask = project.task("${dbType}DeleteDataContainer",
            type: com.bmuschko.gradle.docker.tasks.container.DockerRemoveContainer,
            dependsOn: [deleteContainerTask]) {

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

        project.task("${dbType}Down",
            dependsOn: [deleteDataContainerTask]) {

            group: dbGroup
            description: 'Delete database container stack if not already deleted.'
        }
    }
}
