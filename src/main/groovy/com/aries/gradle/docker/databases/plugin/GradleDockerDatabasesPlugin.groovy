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

import com.aries.gradle.docker.databases.plugin.extensions.AbstractDatabase
import org.gradle.api.Plugin
import org.gradle.api.Project

import com.aries.gradle.docker.databases.plugin.extensions.Databases
import com.aries.gradle.docker.databases.plugin.extensions.Db2
import com.aries.gradle.docker.databases.plugin.extensions.Oracle
import com.aries.gradle.docker.databases.plugin.extensions.Postgres
import com.aries.gradle.docker.databases.plugin.extensions.Sqlserver
import org.gradle.api.Task
import org.gradle.api.tasks.StopExecutionException

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
    private createTaskChain_Up(final Project project, final String taskNamePrefix, final String taskGroup, final AbstractDatabase taskGroupExtension) {

        final Task availableDataContainerTask = project.task("${taskNamePrefix}AvailableDataContainer",
            type: com.bmuschko.gradle.docker.tasks.container.DockerInspectContainer) {

            group: taskGroup
            description: "Check if '${taskNamePrefix}' data container is available."

            targetContainerId { taskGroupExtension.dataId() }

            ext.exists = true
            onNext {} // defining so that the output will get piped to nowhere as we don't need it
            onError { err ->
                if (!err.class.simpleName.matches(NOT_PRESENT_REGEX)) {
                    throw err
                } else {
                    ext.exists = false
                    logger.quiet "Container with ID '${taskGroupExtension.dataId()}' is not running or available to inspect."
                }
            }
        }

        final Task availableContainerTask = project.task("${taskNamePrefix}AvailableContainer",
            type: com.bmuschko.gradle.docker.tasks.container.DockerInspectContainer,
            dependsOn: [availableDataContainerTask]) {

            group: taskGroup
            description: "Check if '${taskNamePrefix}' container is available."

            targetContainerId { taskGroupExtension.mainId() }

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
                    logger.quiet "Container with ID '${taskGroupExtension.mainId()}' is not running or available to inspect."
                }
            }
        }

        final Task restartContainerTask = project.task("${taskNamePrefix}RestartContainer",
            type: com.bmuschko.gradle.docker.tasks.container.DockerRestartContainer,
            dependsOn: [availableContainerTask]) {
            onlyIf { availableContainerTask.ext.exists == true &&
                availableContainerTask.ext.inspection.state.running == false }

            group: taskGroup
            description: "Restart '${taskNamePrefix}' container if it is present and not running."

            doFirst {
                ext.startTime = new Date()
            }
            targetContainerId { taskGroupExtension.mainId() }
            timeout = 30000
        }

        // if a previous main/data container is present than the assumption is that
        // the containerImage in question must also be present and so we don't need to check
        // for the existence of its backing containerImage
        final Task listImagesTask = project.task("${taskNamePrefix}ListImages",
            type: com.bmuschko.gradle.docker.tasks.image.DockerListImages,
            dependsOn: [restartContainerTask]) {
            onlyIf { availableDataContainerTask.ext.exists == false ||
                availableContainerTask.ext.exists == false }

            group: taskGroup
            description: "Check if image for '${taskNamePrefix}' exists locally."

            // if both images are the same we only need to search for one,
            // and thus we can filter down the images, otherwise, and due
            // to dockers horrible image filtering,  we'll have to search
            // through all images looking for the 2 we want.
            ext.duplicateImages = false
            doFirst {
                ext.duplicateImages = taskGroupExtension.mainImage().image() == taskGroupExtension.dataImage().image()
                if (ext.duplicateImages) {
                    imageName = taskGroupExtension.mainImage().image()
                }
            }

            // check if both the main and data images we require are already available
            // so that we don't have to pull them further below. we also make an attempt
            // to stop execution of the closure once the necessary images are found as
            // the local image list _can_ be long and we don't want to unnecessarily
            // iterate over all of them.
            ext.mainImageFound = false
            ext.dataImageFound = false
            onNext { img ->
                if (!ext.mainImageFound || !ext.dataImageFound) {
                    img.repoTags.each { rep ->
                        if (rep) {
                            if (!ext.mainImageFound || !ext.dataImageFound) {
                                if (!ext.mainImageFound && rep.first() == taskGroupExtension.mainImage().image()) {
                                    logger.quiet "Image '${taskGroupExtension.mainImage().image()}' for '${taskGroupExtension.mainId()}' was found locally."
                                    ext.mainImageFound = true
                                    if (ext.duplicateImages) {
                                        ext.dataImageFound = true
                                        throw new StopExecutionException();
                                    }
                                }
                                if (!ext.dataImageFound && rep.first() == taskGroupExtension.dataImage().image()) {
                                    logger.quiet "Image '${taskGroupExtension.dataImage().image()}' for '${taskGroupExtension.dataId()}' was found locally."
                                    ext.dataImageFound = true
                                    if (ext.mainImageFound) {
                                        throw new StopExecutionException();
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }


        final Task pullImageTask = project.task("${taskNamePrefix}PullImage",
            type: com.bmuschko.gradle.docker.tasks.image.DockerPullImage,
            dependsOn: [listImagesTask]) {
            onlyIf { availableContainerTask.ext.exists == false &&
                listImagesTask.ext.mainImageFound == false }

            group: taskGroup
            description: "Pull image for '${taskNamePrefix}'."

            repository = taskGroupExtension.mainImage().repository()
            tag = taskGroupExtension.mainImage().tag()
        }

        final Task pullDataImageTask = project.task("${taskNamePrefix}PullDataImage",
            type: com.bmuschko.gradle.docker.tasks.image.DockerPullImage,
            dependsOn: [pullImageTask]) {
            onlyIf { availableDataContainerTask.ext.exists == false &&
                listImagesTask.ext.dataImageFound == false }

            group: taskGroup
            description: "Pull data image for '${taskNamePrefix}'."

            repository = taskGroupExtension.dataImage().repository()
            tag = taskGroupExtension.dataImage().tag()
        }

        final Task removeContainerTask = project.task("${taskNamePrefix}RemoveContainer",
            type: com.bmuschko.gradle.docker.tasks.container.DockerRemoveContainer,
            dependsOn: [pullDataImageTask]) {
            onlyIf { availableContainerTask.ext.exists == true &&
                availableContainerTask.ext.inspection.state.running == false &&
                restartContainerTask.state.didWork == false }

            group: taskGroup
            description: "Remove '${taskNamePrefix}' container."

            removeVolumes = true
            force = true
            targetContainerId { taskGroupExtension.mainId() }

            onError { err ->
                if (!err.class.simpleName.matches(NOT_PRESENT_REGEX)) {
                    throw err
                } else {
                    logger.quiet "Container with ID '${taskGroupExtension.mainId()}' is not available to remove."
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
            description: "Remove '${taskNamePrefix}' data container."

            removeVolumes = true
            force = true
            targetContainerId { taskGroupExtension.dataId() }

            onError { err ->
                if (!err.class.simpleName.matches(NOT_PRESENT_REGEX)) {
                    throw err
                } else {
                    logger.quiet "Container with ID '${taskGroupExtension.dataId()}' is not available to remove."
                }
            }
        }

        final Task createDataContainerTask = project.task("${taskNamePrefix}CreateDataContainer",
            type: com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer,
            dependsOn: [removeDataContainerTask]) {
            onlyIf { availableDataContainerTask.ext.exists == false }

            group: taskGroup
            description: "Create '${taskNamePrefix}' data container."

            targetImageId { taskGroupExtension.dataImage().image() }
            containerName = taskGroupExtension.dataId()
            volumes = ["/var/lib/postgresql/data"]
        }

        final Task createContainerTask = project.task("${taskNamePrefix}CreateContainer",
            type: com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer,
            dependsOn: [createDataContainerTask]) {
            onlyIf { availableContainerTask.ext.exists == false }

            group: taskGroup
            description: "Create '${taskNamePrefix}' container."

            targetImageId { taskGroupExtension.mainImage().image() }
            containerName = taskGroupExtension.mainId()
            volumesFrom = [taskGroupExtension.dataId()]
        }

        final Task startContainerTask = project.task("${taskNamePrefix}StartContainer",
            type: com.bmuschko.gradle.docker.tasks.container.DockerStartContainer,
            dependsOn: [createContainerTask]) {
            onlyIf { createContainerTask.state.didWork }

            group: taskGroup
            description: "Start '${taskNamePrefix}' container."

            doFirst {
                ext.startTime = new Date()
            }
            targetContainerId { taskGroupExtension.mainId() }
        }

        final Task livenessProbeContainerTask = project.task("${taskNamePrefix}LivenessProbeContainer",
            type: com.bmuschko.gradle.docker.tasks.container.extras.DockerLivenessProbeContainer,
            dependsOn: [startContainerTask]) {
            onlyIf { startContainerTask.state.didWork ||
                restartContainerTask.state.didWork }

            group: taskGroup
            description: "Check if '${taskNamePrefix}' container is live."

            targetContainerId { taskGroupExtension.mainId() }

            // only 2 ways this task can kick so we will proceed to configure
            // it based upon which one did actual work
            doFirst {
                since = startContainerTask.state.didWork ?
                    startContainerTask.ext.startTime :
                    restartContainerTask.ext.startTime
            }
            tailCount = 10
            probe(300000, 30000, taskGroupExtension.liveOnLog())
        }

        project.task("${taskNamePrefix}Up",
            dependsOn: [livenessProbeContainerTask]) {
            group: taskGroup
            description: "Start '${taskNamePrefix}' container application if not already started."
        }
    }

    // create required tasks for invoking the "stop" chain.
    private createTaskChain_Stop(final Project project, final String taskNamePrefix, final String taskGroup, final AbstractDatabase taskGroupExtension) {

        final Task stopContainerTask = project.task("${taskNamePrefix}StopContainer",
            type: com.bmuschko.gradle.docker.tasks.container.extras.DockerExecStopContainer) {

            group: taskGroup
            description: "Stop '${taskNamePrefix}' container."

            targetContainerId { taskGroupExtension.mainId() }
            cmd = ['su', 'postgres', "-c", "/usr/local/bin/pg_ctl stop -m fast"]
            successOnExitCodes = [0, 127, 137]
            timeout = 60000
            probe(60000, 10000)
            onNext { output ->
                // pipe output to nowhere for the time being
            }
            onError { err ->
                if (!err.class.simpleName.matches(NOT_PRESENT_REGEX)) {
                    throw err
                } else {
                    logger.quiet "Container with ID '${taskGroupExtension.mainId()}' is not running/available to stop."
                }
            }
        }

        project.task("${taskNamePrefix}Stop",
            dependsOn: [stopContainerTask]) {
            group: taskGroup
            description: "Stop '${taskNamePrefix}' container application if not already stopped."
        }
    }

    // create required tasks for invoking the "down" chain.
    private createTaskChain_Down(final Project project, final String taskNamePrefix, final String taskGroup, final AbstractDatabase taskGroupExtension) {

        final Task deleteContainerTask = project.task("${taskNamePrefix}DeleteContainer",
            type: com.bmuschko.gradle.docker.tasks.container.DockerRemoveContainer) {

            group: taskGroup
            description: "Delete '${taskNamePrefix}' container."

            removeVolumes = true
            force = true
            targetContainerId { taskGroupExtension.mainId() }

            onError { err ->
                if (!err.class.simpleName.matches(NOT_PRESENT_REGEX)) {
                    throw err
                } else {
                    logger.quiet "Container with ID '${taskGroupExtension.mainId()}' is not available to delete."
                }
            }
        }

        final Task deleteDataContainerTask = project.task("${taskNamePrefix}DeleteDataContainer",
            type: com.bmuschko.gradle.docker.tasks.container.DockerRemoveContainer,
            dependsOn: [deleteContainerTask]) {

            group: taskGroup
            description: "Delete '${taskNamePrefix}' data container."

            removeVolumes = true
            force = true
            targetContainerId { taskGroupExtension.dataId() }

            onError { err ->
                if (!err.class.simpleName.matches(NOT_PRESENT_REGEX)) {
                    throw err
                } else {
                    logger.quiet "Container with ID '${taskGroupExtension.dataId()}' is not available to delete."
                }
            }
        }

        project.task("${taskNamePrefix}Down",
            dependsOn: [deleteDataContainerTask]) {

            group: taskGroup
            description: "Delete '${taskNamePrefix}' container application if not already deleted."
        }
    }
}
