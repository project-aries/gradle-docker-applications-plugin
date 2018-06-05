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

package com.aries.gradle.docker.application.plugin

import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.StopExecutionException

import com.aries.gradle.docker.application.plugin.extensions.AbstractApplication

/**
 *  Plugin providing common tasks for starting, stopping, and deleting dockerized applications.
 */
class GradleDockerApplicationPlugin implements Plugin<Project> {

    public static final String EXTENSION_NAME = 'applications'

    public static final String NOT_PRESENT_REGEX = '^(NotModifiedException|NotFoundException)$'

    @Override
    void apply(final Project project) {

        // 1.) apply required plugins
        project.plugins.apply('com.bmuschko.docker-remote-api')

        // 2.) build container for housing ad-hoc applications
        final NamedDomainObjectContainer<AbstractApplication> appContainer = project.container(AbstractApplication)

        // 3.) build plugin extension point from container
        project.extensions.add(EXTENSION_NAME, appContainer)

        project.afterEvaluate {

            // 4.) create all application extension points
            createApplicationExtensionPoints(project, appContainer)

            // 5.) create all application tasks
            createApplicationTasks(project, appContainer)
        }
    }

    /*
     * Create our various application extension points.
     */
    private createApplicationExtensionPoints(final Project project,
                                             final NamedDomainObjectContainer<AbstractApplication> appContainer) {
        appContainer.each { app ->
            project.extensions.add(app.name, app)
        }
    }

    /*
     *  Create common tasks for all applications
     */
    private createApplicationTasks(final Project project,
                                   final NamedDomainObjectContainer<AbstractApplication> appContainer) {
        appContainer.each { app ->

            // commmon variables used by all tasks below
            final String appGroup = "${taskNamePrefix}-${app.id()}"
            final def appExtension = project.extensions.getByName(app.name)

            // create tasks after evaluation so that we can pick up any changes
            // made to our various extension points.
            createTaskChain_Up(project, app.name, appGroup, appExtension)
            createTaskChain_Stop(project, app.name, appGroup, appExtension)
            createTaskChain_Down(project, app.name, appGroup, appExtension)
        }
    }

    // create required tasks for invoking the "up" chain.
    private createTaskChain_Up(final Project project,
                               final String appName,
                               final String appGroup,
                               final AbstractApplication appExtension) {

        final Task availableDataContainerTask = project.task("${appName}AvailableDataContainer",
            type: com.bmuschko.gradle.docker.tasks.container.DockerInspectContainer) {

            group: appGroup
            description: "Check if '${appName}' data container is available."

            targetContainerId { appExtension.dataId() }

            ext.exists = true
            onNext {} // defining so that the output will get piped to nowhere as we don't need it
            onError { err ->
                if (!err.class.simpleName.matches(NOT_PRESENT_REGEX)) {
                    throw err
                } else {
                    ext.exists = false
                    logger.quiet "Container with ID '${appExtension.dataId()}' is not running or available to inspect."
                }
            }
        }

        final Task availableContainerTask = project.task("${appName}AvailableContainer",
            type: com.bmuschko.gradle.docker.tasks.container.DockerInspectContainer,
            dependsOn: [availableDataContainerTask]) {

            group: appGroup
            description: "Check if '${appName}' container is available."

            targetContainerId { appExtension.mainId() }

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
                    logger.quiet "Container with ID '${appExtension.mainId()}' is not running or available to inspect."
                }
            }
        }

        final Task restartContainerTask = project.task("${appName}RestartContainer",
            type: com.bmuschko.gradle.docker.tasks.container.DockerRestartContainer,
            dependsOn: [availableContainerTask]) {
            onlyIf { availableContainerTask.ext.exists == true &&
                availableContainerTask.ext.inspection.state.running == false }

            group: appGroup
            description: "Restart '${appName}' container if it is present and not running."

            doFirst {
                ext.startTime = new Date()
            }
            targetContainerId { appExtension.mainId() }
            timeout = 30000
        }

        // if a previous main/data container is present than the assumption is that
        // the containerImage in question must also be present and so we don't need to check
        // for the existence of its backing containerImage
        final Task listImagesTask = project.task("${appName}ListImages",
            type: com.bmuschko.gradle.docker.tasks.image.DockerListImages,
            dependsOn: [restartContainerTask]) {
            onlyIf { availableDataContainerTask.ext.exists == false ||
                availableContainerTask.ext.exists == false }

            group: appGroup
            description: "Check if image for '${appName}' exists locally."

            // if both images are the same we only need to search for one,
            // and thus we can filter down the images, otherwise, and due
            // to dockers horrible image filtering,  we'll have to search
            // through all images looking for the 2 we want.
            ext.duplicateImages = false
            doFirst {
                ext.duplicateImages = appExtension.mainImage().image() == appExtension.dataImage().image()
                if (ext.duplicateImages) {
                    imageName = appExtension.mainImage().image()
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
                                if (!ext.mainImageFound && rep.first() == appExtension.mainImage().image()) {
                                    logger.quiet "Image '${appExtension.mainImage().image()}' for '${appExtension.mainId()}' was found locally."
                                    ext.mainImageFound = true
                                    if (ext.duplicateImages) {
                                        ext.dataImageFound = true
                                        throw new StopExecutionException();
                                    }
                                }
                                if (!ext.dataImageFound && rep.first() == appExtension.dataImage().image()) {
                                    logger.quiet "Image '${appExtension.dataImage().image()}' for '${appExtension.dataId()}' was found locally."
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


        final Task pullImageTask = project.task("${appName}PullImage",
            type: com.bmuschko.gradle.docker.tasks.image.DockerPullImage,
            dependsOn: [listImagesTask]) {
            onlyIf { availableContainerTask.ext.exists == false &&
                listImagesTask.ext.mainImageFound == false }

            group: appGroup
            description: "Pull image for '${appName}'."

            repository = appExtension.mainImage().repository()
            tag = appExtension.mainImage().tag()
        }

        final Task pullDataImageTask = project.task("${appName}PullDataImage",
            type: com.bmuschko.gradle.docker.tasks.image.DockerPullImage,
            dependsOn: [pullImageTask]) {
            onlyIf { availableDataContainerTask.ext.exists == false &&
                listImagesTask.ext.dataImageFound == false }

            group: appGroup
            description: "Pull data image for '${appName}'."

            repository = appExtension.dataImage().repository()
            tag = appExtension.dataImage().tag()
        }

        final Task removeContainerTask = project.task("${appName}RemoveContainer",
            type: com.bmuschko.gradle.docker.tasks.container.DockerRemoveContainer,
            dependsOn: [pullDataImageTask]) {
            onlyIf { availableContainerTask.ext.exists == true &&
                availableContainerTask.ext.inspection.state.running == false &&
                restartContainerTask.state.didWork == false }

            group: appGroup
            description: "Remove '${appName}' container."

            removeVolumes = true
            force = true
            targetContainerId { appExtension.mainId() }

            onError { err ->
                if (!err.class.simpleName.matches(NOT_PRESENT_REGEX)) {
                    throw err
                } else {
                    logger.quiet "Container with ID '${appExtension.mainId()}' is not available to remove."
                }
            }
        }

        final Task removeDataContainerTask = project.task("${appName}RemoveDataContainer",
            type: com.bmuschko.gradle.docker.tasks.container.DockerRemoveContainer,
            dependsOn: [removeContainerTask]) {
            onlyIf { availableDataContainerTask.ext.exists == true &&
                restartContainerTask.state.didWork == false &&
                removeContainerTask.state.didWork == true }

            group: appGroup
            description: "Remove '${appName}' data container."

            removeVolumes = true
            force = true
            targetContainerId { appExtension.dataId() }

            onError { err ->
                if (!err.class.simpleName.matches(NOT_PRESENT_REGEX)) {
                    throw err
                } else {
                    logger.quiet "Container with ID '${appExtension.dataId()}' is not available to remove."
                }
            }
        }

        final Task createDataContainerTask = project.task("${appName}CreateDataContainer",
            type: com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer,
            dependsOn: [removeDataContainerTask]) {
            onlyIf { availableDataContainerTask.ext.exists == false }

            group: appGroup
            description: "Create '${appName}' data container."

            targetImageId { appExtension.dataImage().image() }
            containerName = appExtension.dataId()
            volumes = ["/var/lib/postgresql/data"]
        }

        final Task createContainerTask = project.task("${appName}CreateContainer",
            type: com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer,
            dependsOn: [createDataContainerTask]) {
            onlyIf { availableContainerTask.ext.exists == false }

            group: appGroup
            description: "Create '${appName}' container."

            targetImageId { appExtension.mainImage().image() }
            containerName = appExtension.mainId()
            volumesFrom = [appExtension.dataId()]
        }

        final Task startContainerTask = project.task("${appName}StartContainer",
            type: com.bmuschko.gradle.docker.tasks.container.DockerStartContainer,
            dependsOn: [createContainerTask]) {
            onlyIf { createContainerTask.state.didWork }

            group: appGroup
            description: "Start '${appName}' container."

            doFirst {
                ext.startTime = new Date()
            }
            targetContainerId { appExtension.mainId() }
        }

        final Task livenessProbeContainerTask = project.task("${appName}LivenessProbeContainer",
            type: com.bmuschko.gradle.docker.tasks.container.extras.DockerLivenessProbeContainer,
            dependsOn: [startContainerTask]) {
            onlyIf { (startContainerTask.state.didWork ||
                restartContainerTask.state.didWork) && appExtension.liveOnLog() }

            group: appGroup
            description: "Check if '${appName}' container is live."

            targetContainerId { appExtension.mainId() }

            doFirst {

                // only 2 ways this task can kick so we will proceed to configure
                // it based upon which one did actual work
                since = startContainerTask.state.didWork ?
                    startContainerTask.ext.startTime :
                    restartContainerTask.ext.startTime

                tailCount = 10
                probe(300000, 30000, appExtension.liveOnLog())
            }
        }

        project.task("${appName}Up",
            dependsOn: [livenessProbeContainerTask]) {
            group: appGroup
            description: "Start '${appName}' container application if not already started."
        }
    }

    // create required tasks for invoking the "stop" chain.
    private createTaskChain_Stop(final Project project,
                                 final String appName,
                                 final String appGroup,
                                 final AbstractApplication appExtension) {

        final Task stopContainerTask = project.task("${appName}StopContainer",
            type: com.bmuschko.gradle.docker.tasks.container.extras.DockerExecStopContainer) {

            group: appGroup
            description: "Stop '${appName}' container."

            targetContainerId { appExtension.mainId() }
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
                    logger.quiet "Container with ID '${appExtension.mainId()}' is not running/available to stop."
                }
            }
        }

        project.task("${appName}Stop",
            dependsOn: [stopContainerTask]) {
            group: appGroup
            description: "Stop '${appName}' container application if not already stopped."
        }
    }

    // create required tasks for invoking the "down" chain.
    private createTaskChain_Down(final Project project,
                                 final String appName,
                                 final String appGroup,
                                 final AbstractApplication appExtension) {

        final Task deleteContainerTask = project.task("${appName}DeleteContainer",
            type: com.bmuschko.gradle.docker.tasks.container.DockerRemoveContainer) {

            group: appGroup
            description: "Delete '${appName}' container."

            removeVolumes = true
            force = true
            targetContainerId { appExtension.mainId() }

            onError { err ->
                if (!err.class.simpleName.matches(NOT_PRESENT_REGEX)) {
                    throw err
                } else {
                    logger.quiet "Container with ID '${appExtension.mainId()}' is not available to delete."
                }
            }
        }

        final Task deleteDataContainerTask = project.task("${appName}DeleteDataContainer",
            type: com.bmuschko.gradle.docker.tasks.container.DockerRemoveContainer,
            dependsOn: [deleteContainerTask]) {

            group: appGroup
            description: "Delete '${appName}' data container."

            removeVolumes = true
            force = true
            targetContainerId { appExtension.dataId() }

            onError { err ->
                if (!err.class.simpleName.matches(NOT_PRESENT_REGEX)) {
                    throw err
                } else {
                    logger.quiet "Container with ID '${appExtension.dataId()}' is not available to delete."
                }
            }
        }

        project.task("${appName}Down",
            dependsOn: [deleteDataContainerTask]) {

            group: appGroup
            description: "Delete '${appName}' container application if not already deleted."
        }
    }
}
