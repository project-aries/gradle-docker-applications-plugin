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

import com.aries.gradle.docker.application.plugin.domain.DataContainer
import com.aries.gradle.docker.application.plugin.domain.MainContainer
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.StopExecutionException

import com.bmuschko.gradle.docker.tasks.container.extras.DockerExecStopContainer
import com.bmuschko.gradle.docker.tasks.container.extras.DockerLivenessProbeContainer
import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
import com.bmuschko.gradle.docker.tasks.container.DockerInspectContainer
import com.bmuschko.gradle.docker.tasks.container.DockerRemoveContainer
import com.bmuschko.gradle.docker.tasks.container.DockerRestartContainer
import com.bmuschko.gradle.docker.tasks.container.DockerStartContainer
import com.bmuschko.gradle.docker.tasks.image.DockerListImages
import com.bmuschko.gradle.docker.tasks.image.DockerPullImage

import com.aries.gradle.docker.application.plugin.domain.AbstractApplication

/**
 *  Plugin providing domain tasks for starting (Up), stopping (Stop), and deleting (Down) dockerized applications.
 */
class GradleDockerApplicationPlugin implements Plugin<Project> {

    public static final String EXTENSION_NAME = 'applications'

    public static final String NOT_PRESENT_REGEX = '^(NotModifiedException|NotFoundException)$'

    @Override
    void apply(final Project project) {

        // 1.) apply required plugins
        project.plugins.apply('com.bmuschko.docker-remote-api')

        // 2.) build container for housing ad-hoc applications
        final NamedDomainObjectContainer<AbstractApplication> appContainers = project.container(AbstractApplication)

        // 3.) build plugin extension point from container
        project.extensions.add(EXTENSION_NAME, appContainers)

        project.afterEvaluate {

            // 4.) create all application extension points
            createApplicationExtensionPoints(project, appContainers)

            // 5.) create all application tasks
            createApplicationTasks(project, appContainers)
        }
    }

    /*
     * Create our various application extension points which are currently only
     * available AFTER evaluation has occurred..
     */
    private createApplicationExtensionPoints(final Project project,
                                             final NamedDomainObjectContainer<AbstractApplication> appContainers) {
        appContainers.each { app ->
            project.extensions.add(app.name, app)
        }
    }

    /*
     *  Create domain tasks for all applications
     */
    private createApplicationTasks(final Project project,
                                   final NamedDomainObjectContainer<AbstractApplication> appContainers) {
        appContainers.each { app ->

            // commmon variables used by all tasks below
            final String appGroup = "${app.name}-${app.id()}"
            final AbstractApplication appContainer = project.extensions.getByName(app.name)

            // create tasks after evaluation so that we can pick up any changes
            // made to our various extension points.
            buildTaskChainFor_Up(project, app.name, appGroup, appContainer)
            buildTaskChainFor_Stop(project, app.name, appGroup, appContainer)
            buildTaskChainFor_Down(project, app.name, appGroup, appContainer)
        }
    }

    // create required tasks for invoking the "up" chain.
    private buildTaskChainFor_Up(final Project project,
                                 final String appName,
                                 final String appGroup,
                                 final AbstractApplication appContainer) {

        // Must be run after evalution has happened but prior to tasks
        // being built. This ensures our main and data container were
        // properly setup and in the case of the latter we will inherit
        // its properties from the former it wasn't defined.
        appContainer.sanityCheck()

        final DockerInspectContainer availableDataContainerTask = project.task("${appName}AvailableDataContainer",
            type: DockerInspectContainer) {

            group: appGroup
            description: "Check if '${appName}' data container is available."

            targetContainerId { appContainer.dataId() }

            ext.exists = true
            onNext {} // defining so that the output will get piped to nowhere as we don't need it
            onError { err ->
                if (!err.class.simpleName.matches(NOT_PRESENT_REGEX)) {
                    throw err
                } else {
                    ext.exists = false
                    logger.quiet "Container with ID '${appContainer.dataId()}' is not running or available to inspect."
                }
            }
        }

        final DockerInspectContainer availableContainerTask = project.task("${appName}AvailableContainer",
            type: DockerInspectContainer,
            dependsOn: [availableDataContainerTask]) {

            group: appGroup
            description: "Check if '${appName}' container is available."

            targetContainerId { appContainer.mainId() }

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
                    logger.quiet "Container with ID '${appContainer.mainId()}' is not running or available to inspect."
                }
            }
        }

        final DockerRestartContainer restartContainerTask = project.task("${appName}RestartContainer",
            type: DockerRestartContainer,
            dependsOn: [availableContainerTask]) {
            onlyIf { availableContainerTask.ext.exists == true &&
                availableContainerTask.ext.inspection.state.running == false }

            group: appGroup
            description: "Restart '${appName}' container if it is present and not running."

            doFirst {
                ext.startTime = new Date()
            }
            targetContainerId { appContainer.mainId() }
            timeout = 30000
        }

        // if a previous main/data container is present than the assumption is that
        // the containerImage in question must also be present and so we don't need to check
        // for the existence of its backing containerImage
        final DockerListImages listImagesTask = project.task("${appName}ListImages",
            type: DockerListImages,
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
                ext.duplicateImages = appContainer.main().image() == appContainer.data().image()
                if (ext.duplicateImages) {
                    imageName = appContainer.main().image()
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
                                if (!ext.mainImageFound && rep.first() == appContainer.main().image()) {
                                    logger.quiet "Image '${appContainer.main().image()}' for '${appContainer.mainId()}' was found locally."
                                    ext.mainImageFound = true
                                    if (ext.duplicateImages) {
                                        ext.dataImageFound = true
                                        throw new StopExecutionException();
                                    }
                                }
                                if (!ext.dataImageFound && rep.first() == appContainer.data().image()) {
                                    logger.quiet "Image '${appContainer.data().image()}' for '${appContainer.dataId()}' was found locally."
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


        final DockerPullImage pullImageTask = project.task("${appName}PullImage",
            type: DockerPullImage,
            dependsOn: [listImagesTask]) {
            onlyIf { availableContainerTask.ext.exists == false &&
                listImagesTask.ext.mainImageFound == false }

            group: appGroup
            description: "Pull image for '${appName}'."

            repository = appContainer.main().repository()
            tag = appContainer.main().tag()
        }

        final DockerPullImage pullDataImageTask = project.task("${appName}PullDataImage",
            type: DockerPullImage,
            dependsOn: [pullImageTask]) {
            onlyIf { availableDataContainerTask.ext.exists == false &&
                listImagesTask.ext.dataImageFound == false }

            group: appGroup
            description: "Pull data image for '${appName}'."

            repository = appContainer.data().repository()
            tag = appContainer.data().tag()
        }

        final DockerRemoveContainer removeContainerTask = project.task("${appName}RemoveContainer",
            type: DockerRemoveContainer,
            dependsOn: [pullDataImageTask]) {
            onlyIf { availableContainerTask.ext.exists == true &&
                availableContainerTask.ext.inspection.state.running == false &&
                restartContainerTask.state.didWork == false }

            group: appGroup
            description: "Remove '${appName}' container."

            removeVolumes = true
            force = true
            targetContainerId { appContainer.mainId() }

            onError { err ->
                if (!err.class.simpleName.matches(NOT_PRESENT_REGEX)) {
                    throw err
                } else {
                    logger.quiet "Container with ID '${appContainer.mainId()}' is not available to remove."
                }
            }
        }

        final DockerRemoveContainer removeDataContainerTask = project.task("${appName}RemoveDataContainer",
            type: DockerRemoveContainer,
            dependsOn: [removeContainerTask]) {
            onlyIf { availableDataContainerTask.ext.exists == true &&
                restartContainerTask.state.didWork == false &&
                removeContainerTask.state.didWork == true }

            group: appGroup
            description: "Remove '${appName}' data container."

            removeVolumes = true
            force = true
            targetContainerId { appContainer.dataId() }

            onError { err ->
                if (!err.class.simpleName.matches(NOT_PRESENT_REGEX)) {
                    throw err
                } else {
                    logger.quiet "Container with ID '${appContainer.dataId()}' is not available to remove."
                }
            }
        }

        final DockerCreateContainer createDataContainerTask = project.task("${appName}CreateDataContainer",
            type: DockerCreateContainer,
            dependsOn: [removeDataContainerTask]) {
            onlyIf { availableDataContainerTask.ext.exists == false }

            group: appGroup
            description: "Create '${appName}' data container."

            targetImageId { appContainer.data().image() }
            doFirst {
                containerName = appContainer.dataId()
            }
        }
        appContainer.data().createConfigs.each { createContainerTask.configure(it) }

        final DockerCreateContainer createContainerTask = project.task("${appName}CreateContainer",
            type: DockerCreateContainer,
            dependsOn: [createDataContainerTask]) {
            onlyIf { availableContainerTask.ext.exists == false }

            group: appGroup
            description: "Create '${appName}' container."

            targetImageId { appContainer.main().image() }
            doFirst {
                containerName = appContainer.mainId()
                volumesFrom = [appContainer.dataId()]
            }
        }
        appContainer.main().createConfigs.each { createContainerTask.configure(it) }

        final DockerStartContainer startContainerTask = project.task("${appName}StartContainer",
            type: DockerStartContainer,
            dependsOn: [createContainerTask]) {
            onlyIf { createContainerTask.state.didWork }

            group: appGroup
            description: "Start '${appName}' container."

            doFirst {
                ext.startTime = new Date()
            }
            targetContainerId { appContainer.mainId() }
        }
        appContainer.main().startConfigs.each { startContainerTask.configure(it) }

        final DockerLivenessProbeContainer livenessProbeContainerTask = project.task("${appName}LivenessProbeContainer",
            type: DockerLivenessProbeContainer,
            dependsOn: [startContainerTask]) {
            onlyIf { (startContainerTask.state.didWork ||
                restartContainerTask.state.didWork) && appContainer.main().livenessConfigs }

            group: appGroup
            description: "Check if '${appName}' container is live."

            targetContainerId { appContainer.mainId() }

            // only 2 ways this task can kick so we will proceed to configure
            // the `since` option based upon which one did actual work
            doFirst {
                since = startContainerTask.state.didWork ?
                    startContainerTask.ext.startTime :
                    restartContainerTask.ext.startTime
            }
        }
        appContainer.main().livenessConfigs.each { livenessProbeContainerTask.configure(it) }

        project.task("${appName}Up",
            dependsOn: [livenessProbeContainerTask]) {
            outputs.upToDateWhen { false }

            group: appGroup
            description: "Start '${appName}' container application if not already started."
        }
    }

    // create required tasks for invoking the "stop" chain.
    private buildTaskChainFor_Stop(final Project project,
                                   final String appName,
                                   final String appGroup,
                                   final AbstractApplication appExtension) {

        final DockerExecStopContainer stopContainerTask = project.task("${appName}StopContainer",
            type: DockerExecStopContainer) {

            group: appGroup
            description: "Stop '${appName}' container."

            targetContainerId { appExtension.mainId() }
            onNext { output ->
                // pipe output to nowhere for the time being
            }
            onError { err ->
                if (!err.class.simpleName.matches(NOT_PRESENT_REGEX)) {
                    throw err
                } else {
                    logger.quiet "Container with ID '${appExtension.mainId()}' is not running or available to stop."
                }
            }
        }
        appExtension.main().stopConfigs.each { stopContainerTask.configure(it) }

        project.task("${appName}Stop",
            dependsOn: [stopContainerTask]) {
            outputs.upToDateWhen { false }

            group: appGroup
            description: "Stop '${appName}' container application if not already paused."
        }
    }

    // create required tasks for invoking the "down" chain.
    private buildTaskChainFor_Down(final Project project,
                                   final String appName,
                                   final String appGroup,
                                   final AbstractApplication appExtension) {

        final DockerRemoveContainer deleteContainerTask = project.task("${appName}DeleteContainer",
            type: DockerRemoveContainer) {

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

        final DockerRemoveContainer deleteDataContainerTask = project.task("${appName}DeleteDataContainer",
            type: DockerRemoveContainer,
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
            outputs.upToDateWhen { false }

            group: appGroup
            description: "Delete '${appName}' container application if not already deleted."
        }
    }
}
