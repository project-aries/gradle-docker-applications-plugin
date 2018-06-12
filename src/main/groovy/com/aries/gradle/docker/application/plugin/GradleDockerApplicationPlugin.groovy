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

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

import static com.aries.gradle.docker.application.plugin.GradleDockerApplicationPluginUtils.randomString
import static com.bmuschko.gradle.docker.utils.IOUtils.getProgressLogger

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

import org.gradle.api.GradleException
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.StopExecutionException

/**
 *  Plugin providing common tasks for starting (*Up), stopping (*Stop), and deleting (*Down) dockerized applications.
 */
class GradleDockerApplicationPlugin implements Plugin<Project> {

    public static final String EXTENSION_NAME = 'applications'

    public static final String NOT_PRESENT_REGEX = '^(NotModifiedException|NotFoundException)$'

    @Override
    void apply(final Project project) {

        // 1.) apply required plugins
        project.plugins.apply('com.bmuschko.docker-remote-api')

        // 2.) build domain-container for housing ad-hoc applications
        final NamedDomainObjectContainer<AbstractApplication> appContainers = project.container(AbstractApplication)

        // 3.) build plugin extension point from domain-container
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
            //buildLockTasks(project, app.name, appGroup, appContainer)
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

        // build our locking tasks for multi-project wide execution which in turn are
        // specific to THIS chain of tasks.
        final Task acquireExecutionLockTask = buildAcquireExecutionLockTask(project, appName, appGroup, appContainer)
        final Task releaseExecutionLockTask = buildReleaseExecutionLockTask(project, appName, appGroup, appContainer)

        final DockerInspectContainer availableDataContainerTask = project.task("${appName}AvailableDataContainer",
            type: DockerInspectContainer,
            dependsOn: [acquireExecutionLockTask]) {

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
                                    ext.mainImageFound = true
                                    if (ext.duplicateImages) {
                                        logger.quiet "Images for '${appContainer.mainId()}' and '${appContainer.dataId()}' were found locally."
                                        ext.dataImageFound = true
                                        throw new StopExecutionException();
                                    } else {
                                        logger.quiet "Image '${appContainer.main().image()}' for '${appContainer.mainId()}' was found locally."
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
            onComplete {

                // print to stdout any images that were not found locally that need a pull
                if (ext.duplicateImages) {
                    if (!ext.mainImageFound) {
                        logger.quiet "Images for '${appContainer.mainId()}' and '${appContainer.dataId()}' were not found locally: pull required."
                    }
                } else {
                    if (!ext.mainImageFound) {
                        logger.quiet "Image for '${appContainer.mainId()}' was not found locally: pull required."
                    }
                    if (!ext.dataImageFound) {
                        logger.quiet "Image for '${appContainer.dataId()}' was not found locally: pull required."
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
            onError { err ->
                if (err.class.simpleName.matches(NOT_PRESENT_REGEX)) {
                    throw new GradleException("Image '${appContainer.main().image()}' for '${appContainer.mainId()}' was not found remotely.", err)
                } else {
                    throw err
                }
            }
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
            onError { err ->
                if (err.class.simpleName.matches(NOT_PRESENT_REGEX)) {
                    throw new GradleException("Image '${appContainer.data().image()}' for '${appContainer.dataId()}' was not found remotely.", err)
                } else {
                    throw err
                }
            }
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

        final DockerLivenessProbeContainer livenessProbeContainerTask = project.task("${appName}LivenessProbeContainer",
            type: DockerLivenessProbeContainer,
            dependsOn: [startContainerTask]) {
            onlyIf { startContainerTask.state.didWork ||
                restartContainerTask.state.didWork }

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

        final Task upTask = project.task("${appName}Up",
            dependsOn: [livenessProbeContainerTask]) {
            outputs.upToDateWhen { false }

            group: appGroup
            description: "Start '${appName}' container application if not already started."

            doLast {

                // 1.) Set the last used "inspection" for potential downstream use
                if (livenessProbeContainerTask.state.didWork) {
                    ext.inspection = livenessProbeContainerTask.lastInspection()
                } else if (availableContainerTask.ext.inspection) {
                    ext.inspection = availableContainerTask.ext.inspection
                } else {
                    throw new GradleException('No task found to inspect container: was this expected?')
                }

                // 2.) set handful of variables for easy access and downstream use
                ext.id = ext.inspection.id
                ext.name = ext.inspection.name.replaceFirst('/', '')
                ext.image = ext.inspection.getConfig().image
                ext.command = (ext.inspection.getConfig().getEntrypoint()) ? ext.inspection.getConfig().getEntrypoint().join(' ') : null
                if (ext.inspection.getArgs()) {
                    if (!ext.command) {ext.command = ""}
                    ext.command = ("${ext.command} " + ext.inspection.getArgs().join(' ')).trim()
                }
                ext.created = ext.inspection.created
                ext.address = ext.inspection.getNetworkSettings().getGateway()
                ext.ports = [:]
                if (ext.inspection.getNetworkSettings().getPorts()) {
                    ext.inspection.getNetworkSettings().getPorts().getBindings().each { k, v ->
                        def key = '' + k.getPort()
                        def value = '' + ( v ? v[0].hostPortSpec : 0)
                        ext.ports.put(key, value)
                    }
                }
                ext.links = [:]
                if (ext.inspection.hostConfig.links) {
                    ext.inspection.hostConfig.links.each {
                        ext.links.put(it.name, it.alias)
                    }
                }

                // 3.) print all variables to stdout as an indication that we are now live
                String banner = '====='
                ext.id.length().times { banner += '=' }

                logger.quiet banner
                logger.quiet "ID = ${ext.id}"
                logger.quiet "NAME = ${ext.name}"
                logger.quiet "IMAGE = ${ext.image}"
                logger.quiet "COMMAND = ${ext.command}"
                logger.quiet "CREATED = ${ext.created}"
                logger.quiet "ADDRESS = ${ext.address}"
                logger.quiet "PORTS = " + ((ext.ports) ? ext.ports.collect { k, v -> "${v}->${k}"}.join(',') : ext.ports.toString())
                logger.quiet "LINKS = " + ((ext.links) ? ext.links.collect { k, v -> "${k}:${v}"}.join(',') : ext.links.toString())
                logger.quiet banner
            }
        }
        upTask.finalizedBy(releaseExecutionLockTask)
    }

    // create required tasks for invoking the "stop" chain.
    private buildTaskChainFor_Stop(final Project project,
                                   final String appName,
                                   final String appGroup,
                                   final AbstractApplication appContainer) {

        // build our locking tasks for multi-project wide execution which in turn are
        // specific to THIS chain of tasks.
        final Task acquireExecutionLockTask = buildAcquireExecutionLockTask(project, appName, appGroup, appContainer)
        final Task releaseExecutionLockTask = buildReleaseExecutionLockTask(project, appName, appGroup, appContainer)

        final DockerExecStopContainer execStopContainerTask = project.task("${appName}ExecStopContainer",
            type: DockerExecStopContainer,
            dependsOn: [acquireExecutionLockTask]) {

            group: appGroup
            description: "Stop '${appName}' container."

            targetContainerId { appContainer.mainId() }
            onNext { output ->
                // pipe output to nowhere for the time being
            }
            onError { err ->
                if (!err.class.simpleName.matches(NOT_PRESENT_REGEX)) {
                    throw err
                } else {
                    logger.quiet "Container with ID '${appContainer.mainId()}' is not running or available to stop."
                }
            }
        }
        appContainer.main().stopConfigs.each { execStopContainerTask.configure(it) }

        final Task stopTask = project.task("${appName}Stop",
            dependsOn: [execStopContainerTask]) {
            outputs.upToDateWhen { false }

            group: appGroup
            description: "Stop '${appName}' container application if not already paused."
        }
        stopTask.finalizedBy(releaseExecutionLockTask)
    }

    // create required tasks for invoking the "down" chain.
    private buildTaskChainFor_Down(final Project project,
                                   final String appName,
                                   final String appGroup,
                                   final AbstractApplication appContainer) {

        // build our locking tasks for multi-project wide execution which in turn are
        // specific to THIS chain of tasks.
        final Task acquireExecutionLockTask = buildAcquireExecutionLockTask(project, appName, appGroup, appContainer)
        final Task releaseExecutionLockTask = buildReleaseExecutionLockTask(project, appName, appGroup, appContainer)

        final DockerRemoveContainer deleteContainerTask = project.task("${appName}DeleteContainer",
            type: DockerRemoveContainer,
            dependsOn: [acquireExecutionLockTask]) {

            group: appGroup
            description: "Delete '${appName}' container."

            removeVolumes = true
            force = true
            targetContainerId { appContainer.mainId() }

            onError { err ->
                if (!err.class.simpleName.matches(NOT_PRESENT_REGEX)) {
                    throw err
                } else {
                    logger.quiet "Container with ID '${appContainer.mainId()}' is not available to delete."
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
            targetContainerId { appContainer.dataId() }

            onError { err ->
                if (!err.class.simpleName.matches(NOT_PRESENT_REGEX)) {
                    throw err
                } else {
                    logger.quiet "Container with ID '${appContainer.dataId()}' is not available to delete."
                }
            }
        }

        final Task downTask = project.task("${appName}Down",
            dependsOn: [deleteDataContainerTask]) {
            outputs.upToDateWhen { false }

            group: appGroup
            description: "Delete '${appName}' container application if not already deleted."
        }
        downTask.finalizedBy(releaseExecutionLockTask)
    }

    // create task which will acquire an execution lock for a given task chain
    private Task buildAcquireExecutionLockTask(final Project project,
                                               final String appName,
                                               final String appGroup,
                                               final AbstractApplication appContainer) {

        // using random string as this method is called ad-hoc in multiple places
        // and so the name must be unique but still named appropriately.
        return project.task("${appName}AcquireExecutionLock_${randomString(null)}") {
            outputs.upToDateWhen { false }

            group: appGroup
            description: "Acquire execution lock for '${appName}'."

            doLast {
                logger.quiet "Acquiring execution lock for '${appName}'."

                final String lockName = appContainer.mainId()
                if(!project.gradle.ext.has(lockName)) {
                    synchronized (GradleDockerApplicationPlugin) {
                        if(!project.gradle.ext.has(lockName)) {
                            final AtomicBoolean executionLock = new AtomicBoolean(false);
                            project.gradle.ext.set(lockName, executionLock)
                        }
                    }
                }

                final def progressLogger = getProgressLogger(project, GradleDockerApplicationPlugin)
                progressLogger.started()

                int pollTimes = 0
                long pollInterval = 5000
                long totalMillis = 0
                final AtomicBoolean executionLock = project.gradle.ext.get(lockName)
                while(!executionLock.compareAndSet(false, true)) {
                    pollTimes += 1

                    totalMillis = pollTimes * pollInterval
                    long totalMinutes = TimeUnit.MILLISECONDS.toMinutes(totalMillis)

                    progressLogger.progress("Waiting on lock for ${totalMinutes}m...")
                    sleep(pollInterval)
                }
                progressLogger.completed()

                logger.quiet "Lock took ${totalMillis}m to acquire."
            }
        }
    }

    // create task which will release an execution lock for a given task chain
    private Task buildReleaseExecutionLockTask(final Project project,
                                               final String appName,
                                               final String appGroup,
                                               final AbstractApplication appContainer) {

        // using random string as this method is called ad-hoc in multiple places
        // and so the name must be unique but still named appropriately.
        return project.task("${appName}ReleaseExecutionLock_${randomString(null)}") {
            outputs.upToDateWhen { false }

            group: appGroup
            description: "Release execution lock for '${appName}'."

            doLast {
                logger.quiet "Releasing execution lock for '${appName}'."

                final String lockName = appContainer.mainId()
                if(project.gradle.ext.has(lockName)) {
                    final AtomicBoolean executionLock = project.gradle.ext.get(lockName)
                    executionLock.set(false)
                } else {
                    throw new GradleException("Failed to find execution lock for '${appName}'.")
                }
            }
        }
    }
}
