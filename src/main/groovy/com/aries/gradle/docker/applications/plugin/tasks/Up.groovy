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

package com.aries.gradle.docker.applications.plugin.tasks

import com.aries.gradle.docker.applications.plugin.domain.AbstractApplication
import com.bmuschko.gradle.docker.tasks.DockerOperation
import com.bmuschko.gradle.docker.tasks.container.*
import com.bmuschko.gradle.docker.tasks.container.extras.DockerLivenessContainer
import com.bmuschko.gradle.docker.tasks.image.DockerListImages
import com.bmuschko.gradle.docker.tasks.image.DockerPullImage
import com.bmuschko.gradle.docker.tasks.network.DockerCreateNetwork
import com.bmuschko.gradle.docker.tasks.network.DockerInspectNetwork
import com.github.dockerjava.api.model.ContainerNetwork
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.StopExecutionException
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider

import static com.aries.gradle.docker.applications.plugin.GradleDockerApplicationsPluginUtils.buildAcquireExecutionLockTask
import static com.aries.gradle.docker.applications.plugin.GradleDockerApplicationsPluginUtils.buildReleaseExecutionLockTask
import static com.aries.gradle.docker.applications.plugin.GradleDockerApplicationsPluginUtils.applyConfigs
import static com.aries.gradle.docker.applications.plugin.GradleDockerApplicationsPluginUtils.throwOnValidError
import static com.aries.gradle.docker.applications.plugin.GradleDockerApplicationsPluginUtils.throwOnValidErrorElseGradleException

/**
 *  Contains single static method to create the `Up` task chain.
 */
final class Up {

    private Up() {
        throw new UnsupportedOperationException("Purposefully not implemented.")
    }

    static List<TaskProvider<Task>> createTaskChain(final Project project,
                                                    final AbstractApplication appContainer) {

        final List<TaskProvider<Task>> taskList = new ArrayList();

        final String appName = appContainer.getName()
        final String dataId = appContainer.dataId()
        final String mainId = appContainer.mainId()
        final String networkName = appContainer.network()

        for (int i = 0; i < appContainer.count(); i++) {
            final TaskProvider<Task> singleTaskChain = _createTaskChain(project, appContainer, appName, dataId, mainId, networkName, "_" + (i + 1))
            taskList.add(singleTaskChain)
        }

        return taskList
    }

    // create required tasks for invoking the "up" chain.
    private static TaskProvider<DockerOperation> _createTaskChain(final Project project,
                                                         final AbstractApplication appContainer,
                                                         final String appName,
                                                         String dataId,
                                                         String mainId,
                                                         final String networkName,
                                                         final String appender) {

        dataId = dataId + appender
        mainId = mainId + appender

        final TaskContainer tasks = project.tasks;
        final TaskProvider<Task> acquireExecutionLockTask = buildAcquireExecutionLockTask(project, appName, mainId)
        final TaskProvider<Task> releaseExecutionLockTask = buildReleaseExecutionLockTask(project, appName, mainId)

        String taskName = "${appName}AvailableDataContainer" + appender
        final TaskProvider<DockerInspectContainer> availableDataContainerTask = tasks.register(taskName, DockerInspectContainer) {

            dependsOn(acquireExecutionLockTask)

            group: appName
            description: "Check if '${appName}' data container is available."

            targetContainerId { dataId }

            ext.exists = false
            ext.inspection = null
            onNext { possibleContainer ->
                ext.exists = true
                ext.inspection = possibleContainer
            }
            onError { err ->
                throwOnValidError(err)
                logger.quiet "Container with ID '${dataId}' is not running or available to inspect."
            }
        }

        taskName = "${appName}AvailableContainer" + appender
        final TaskProvider<DockerInspectContainer> availableContainerTask = tasks.register(taskName, DockerInspectContainer) {

            dependsOn(availableDataContainerTask)

            group: appName
            description: "Check if '${appName}' container is available."

            targetContainerId { mainId }

            ext.exists = false
            ext.inspection = null
            ext.hasNetwork = false
            onNext { possibleContainer ->
                ext.exists = true
                ext.inspection = possibleContainer
                ext.hasNetwork = possibleContainer.getNetworkSettings().getNetworks().containsKey(networkName)
            }
            onError { err ->
                throwOnValidError(err)
                logger.quiet "Container with ID '${mainId}' is not running or available to inspect."
            }
        }

        taskName = "${appName}InspectNetwork" + appender
        final TaskProvider<DockerInspectNetwork> inspectNetworkTask = tasks.register(taskName, DockerInspectNetwork) {
            onlyIf { networkName && !availableContainerTask.get().ext.hasNetwork }

            dependsOn(availableContainerTask)

            group: appName
            description: "Inspect '${appName}' network."

            ext.hasNetwork = true
            networkId = networkName
            onError {
                ext.hasNetwork = false
            }
        }

        taskName = "${appName}CreateNetwork" + appender
        final TaskProvider<DockerCreateNetwork> createNetworkTask = tasks.register(taskName, DockerCreateNetwork) {
            onlyIf { networkName && !inspectNetworkTask.get().ext.hasNetwork }

            dependsOn(inspectNetworkTask)

            group: appName
            description: "Create '${appName}' network."

            networkId = networkName
        }

        taskName = "${appName}RestartContainer" + appender
        final TaskProvider<DockerRestartContainer> restartContainerTask = tasks.register(taskName, DockerRestartContainer) {
            onlyIf {
                availableContainerTask.get().ext.exists == true &&
                    availableContainerTask.get().ext.inspection.state.running == false
            }

            dependsOn(createNetworkTask)

            group: appName
            description: "Restart '${appName}' container if it is present and not running."

            doFirst {
                ext.startTime = new Date()
            }
            targetContainerId { mainId}
            waitTime = 3000
        }

        // if a previous main/data container is present than the assumption is that
        // the containerImage in question must also be present and so we don't need to check
        // for the existence of its backing containerImage
        taskName = "${appName}ListImages" + appender
        final TaskProvider<DockerListImages> listImagesTask = tasks.register(taskName, DockerListImages) {
            onlyIf {
                availableDataContainerTask.get().ext.exists == false ||
                    availableContainerTask.get().ext.exists == false
            }

            dependsOn(restartContainerTask)

            group: appName
            description: "Check if image for '${appName}' exists locally."

            // if both images are the same we only need to search for one,
            // and thus we can filter down the images, otherwise, and due
            // to dockers horrible image filtering,  we'll have to search
            // through all images looking for the 2 we want.
            ext.duplicateImages = false
            imageName.set(project.provider {
                ext.duplicateImages = appContainer.main().image() == appContainer.data().image()
                if (ext.duplicateImages) {
                    imageName.set(appContainer.main().image())
                }
            })

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
                                if (!ext.mainImageFound && rep == appContainer.main().image()) {
                                    ext.mainImageFound = true
                                    if (ext.duplicateImages) {
                                        logger.quiet "Images for '${mainId}' and '${dataId}' were found locally."
                                        ext.dataImageFound = true
                                        throw new StopExecutionException();
                                    } else {
                                        logger.quiet "Image '${appContainer.main().image()}' for '${mainId}' was found locally."
                                    }
                                }
                                if (!ext.dataImageFound && rep == appContainer.data().image()) {
                                    logger.quiet "Image '${appContainer.data().image()}' for '${dataId}' was found locally."
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
                        logger.quiet "Images for '${mainId}' and '${dataId}' were not found locally: pull required."
                    }
                } else {
                    if (!ext.mainImageFound) {
                        logger.quiet "Image for '${mainId}' was not found locally: pull required."
                    }
                    if (!ext.dataImageFound) {
                        logger.quiet "Image for '${dataId}' was not found locally: pull required."
                    }
                }
            }
        }

        taskName = "${appName}PullImage" + appender
        final TaskProvider<DockerPullImage> pullImageTask = tasks.register(taskName, DockerPullImage) {
            onlyIf {
                availableContainerTask.get().ext.exists == false &&
                    listImagesTask.get().ext.mainImageFound == false
            }

            dependsOn(listImagesTask)

            group: appName
            description: "Pull image for '${appName}'."

            repository = appContainer.main().repository()
            tag = appContainer.main().tag()
            onError { err ->
                throwOnValidErrorElseGradleException(err, "Image '${appContainer.main().image()}' for '${mainId}' was not found remotely.")
            }
        }

        taskName = "${appName}PullDataImage" + appender
        final TaskProvider<DockerPullImage> pullDataImageTask = tasks.register(taskName, DockerPullImage) {
            onlyIf {
                availableDataContainerTask.get().ext.exists == false &&
                    listImagesTask.get().ext.dataImageFound == false &&
                    listImagesTask.get().ext.duplicateImages == false
            }

            dependsOn(pullImageTask)

            group: appName
            description: "Pull data image for '${appName}'."

            repository = appContainer.data().repository()
            tag = appContainer.data().tag()
            onError { err ->
                throwOnValidErrorElseGradleException(err, "Image '${appContainer.data().image()}' for '${dataId}' was not found remotely.")
            }
        }

        taskName = "${appName}RemoveContainer" + appender
        final TaskProvider<DockerRemoveContainer> removeContainerTask = tasks.register(taskName, DockerRemoveContainer) {
            onlyIf {
                availableContainerTask.get().ext.exists == true &&
                    availableContainerTask.get().ext.inspection.state.running == false &&
                    restartContainerTask.get().didWork == false
            }

            dependsOn(pullDataImageTask)

            group: appName
            description: "Remove '${appName}' container."

            removeVolumes = true
            force = true
            targetContainerId { mainId }

            onError { err ->
                if (!err.class.simpleName.matches(NOT_PRESENT_REGEX)) {
                    throw err
                } else {
                    logger.quiet "Container with ID '${mainId}' is not available to remove."
                }
            }
        }

        taskName = "${appName}RemoveDataContainer" + appender
        final TaskProvider<DockerRemoveContainer> removeDataContainerTask = tasks.register(taskName, DockerRemoveContainer) {
            onlyIf {
                availableDataContainerTask.get().ext.exists == true &&
                    restartContainerTask.get().didWork == false &&
                    removeContainerTask.get().didWork == true
            }

            dependsOn(removeContainerTask)

            group: appName
            description: "Remove '${appName}' data container."

            removeVolumes = true
            force = true
            targetContainerId { dataId }

            onError { err ->
                throwOnValidError(err)
                logger.quiet "Container with ID '${dataId}' is not available to remove."
            }
        }

        taskName = "${appName}CreateDataContainer" + appender
        final TaskProvider<DockerCreateContainer> createDataContainerTask = tasks.register(taskName, DockerCreateContainer) {
            onlyIf { !availableDataContainerTask.get().ext.exists }

            dependsOn(removeDataContainerTask)

            group: appName
            description: "Create '${appName}' data container."

            network = networkName
            targetImageId { appContainer.data().image() }
            containerName = dataId
        }
        applyConfigs(createDataContainerTask, appContainer.data().createConfigs)

        taskName = "${appName}CreateContainer" + appender
        final TaskProvider<DockerCreateContainer> createContainerTask = tasks.register(taskName, DockerCreateContainer) {
            onlyIf { !availableContainerTask.get().ext.exists }

            dependsOn(createDataContainerTask)

            group: appName
            description: "Create '${appName}' container."

            network = networkName
            targetImageId { appContainer.main().image() }
            containerName = mainId
            volumesFrom = [dataId]
        }
        applyConfigs(createContainerTask, appContainer.main().createConfigs)

        taskName = "${appName}CopyFilesToDataContainer" + appender
        final TaskProvider<DockerCopyFileToContainer> copyFilesToDataContainerTask = tasks.register(taskName, DockerCopyFileToContainer) {
            onlyIf { createDataContainerTask.get().didWork && appContainer.data().filesConfigs.size() > 0 }

            dependsOn(createContainerTask)

            group: appName
            description: "Copy file(s) into '${appName}' data container."

            targetContainerId { dataId }
        }
        applyConfigs(copyFilesToDataContainerTask, appContainer.data().filesConfigs)

        taskName = "${appName}CopyFilesToContainer" + appender
        final TaskProvider<DockerCopyFileToContainer> copyFilesToContainerTask = tasks.register(taskName, DockerCopyFileToContainer) {
            onlyIf { createContainerTask.get().didWork && appContainer.main().filesConfigs.size() > 0 }

            dependsOn(copyFilesToDataContainerTask)

            group: appName
            description: "Copy file(s) into '${appName}' container."

            targetContainerId { mainId }
        }
        applyConfigs(copyFilesToContainerTask, appContainer.main().filesConfigs)

        taskName = "${appName}ConnectNetwork" + appender
        final TaskProvider<DockerOperation> connectNetworkTask = tasks.register(taskName, DockerOperation) {
            onlyIf {
                networkName && !availableContainerTask.get().ext.hasNetwork &&
                    (restartContainerTask.get().didWork ||
                        ((createNetworkTask.get().didWork ||
                            inspectNetworkTask.get().ext.hasNetwork) &&
                            !createContainerTask.get().didWork))
            }

            dependsOn(copyFilesToContainerTask)

            group: appName
            description: "Connect '${appName}' network."

            onNext { dockerCli ->

                logger.quiet "Connecting network '${mainId}'."

                dockerCli.connectToNetworkCmd()
                    .withNetworkId(networkName)
                    .withContainerId(mainId)
                    .exec()
            }
        }

        taskName = "${appName}StartContainer" + appender
        final TaskProvider<DockerStartContainer> startContainerTask = tasks.register(taskName, DockerStartContainer) {
            onlyIf { createContainerTask.get().didWork }

            dependsOn(connectNetworkTask)

            group: appName
            description: "Start '${appName}' container."

            doFirst {
                ext.startTime = new Date()
            }
            targetContainerId { mainId }
        }

        taskName = "${appName}LivenessContainer" + appender
        final TaskProvider<DockerLivenessContainer> livenessContainerTask = tasks.register(taskName, DockerLivenessContainer) {
            onlyIf {
                startContainerTask.get().didWork ||
                    restartContainerTask.get().didWork
            }

            dependsOn(startContainerTask)

            group: appName
            description: "Check if '${appName}' container is live."

            targetContainerId { mainId }

            // only 2 ways this task can kick so we will proceed to configure
            // the `since` option based ONLY upon a "restart" scenario as we will
            // use it to determine where in the logs we should start from whereas
            // in the "start" scenario we simply start from the very beginning
            // of the logs docker gives us.
            since.set(project.provider {
                restartContainerTask.get().didWork ?
                    restartContainerTask.get().ext.startTime :
                    null
            })

            doFirst {

                // pause done to allow the container to come up and potentially
                // exit (e.g. container that has no entrypoint or cmd defined).
                sleep(2000)
            }
            onComplete {

                // though we should be live at this point we sleep for
                // another 2 seconds to give the potential application
                // some breathing room before we start hammering away
                // on it with potential requests.
                sleep(2000)
            }
        }
        applyConfigs(livenessContainerTask, appContainer.main().livenessConfigs)

        taskName = "${appName}ExecContainer" + appender
        final TaskProvider<DockerExecContainer> execContainerTask = tasks.register(taskName, DockerExecContainer) {
            onlyIf {
                livenessContainerTask.get().didWork &&
                    appContainer.main().execConfigs.size() > 0 &&
                    !restartContainerTask.get().didWork
            }

            dependsOn(livenessContainerTask)

            group: appName
            description: "Execute commands within '${appName}' container."

            targetContainerId { mainId }

            onComplete {

                // sleeping for 2 seconds just in-case any command caused this container to
                // come down, potentially gracefully, before we presume things are live.
                sleep(2000)
            }
        }
        applyConfigs(execContainerTask, appContainer.main().execConfigs)

        taskName = "${appName}Up" + appender
        return tasks.register(taskName, DockerOperation) {
            outputs.upToDateWhen { false }

            dependsOn(execContainerTask)
            finalizedBy(releaseExecutionLockTask)

            group: appName
            description: "Start '${appName}' container application if not already started."

            onNext { dockerClient ->

                // 1.) Set the last used "inspection" for potential downstream use
                if (execContainerTask.get().didWork) {

                    // if the `execContainerTask` task kicked we need to
                    // make an additional inspection call to ensure things
                    // are still live and running just to be on the safe side.
                    ext.inspection = dockerClient.inspectContainerCmd(mainId).exec()
                    if (!ext.inspection.state.running) {
                        throw new GradleException("The 'main' container was NOT in a running state after exec(s) finished. Was this expected?")
                    }
                } else if (livenessContainerTask.get().didWork) {
                    ext.inspection = livenessContainerTask.get().lastInspection()
                } else if (connectNetworkTask.get().didWork) {
                    ext.inspection = dockerClient.inspectContainerCmd(mainId).exec()
                } else if (availableContainerTask.get().ext.inspection) {
                    ext.inspection = availableContainerTask.get().ext.inspection
                } else {
                    throw new GradleException('No task found that inspected container: was this expected?')
                }

                // 2.) set handful of variables for easy access and downstream use
                ext.id = ext.inspection.id
                ext.name = ext.inspection.name.replaceFirst('/', '')
                ext.image = ext.inspection.getConfig().image
                ext.command = (ext.inspection.getConfig().getEntrypoint()) ? ext.inspection.getConfig().getEntrypoint().join(' ') : null
                if (ext.inspection.getArgs()) {
                    if (!ext.command) {
                        ext.command = ""
                    }
                    ext.command = ("${ext.command} " + ext.inspection.getArgs().join(' ')).trim()
                }
                ext.created = ext.inspection.created
                ext.ports = [:]
                if (ext.inspection.getNetworkSettings().getPorts()) {
                    ext.inspection.getNetworkSettings().getPorts().getBindings().each { k, v ->
                        def key = '' + k.getPort()
                        def value = '' + (v ? v[0].hostPortSpec : 0)
                        ext.ports.put(key, value)
                    }
                }

                // find the proper network to use for downstream consumption
                if (ext.inspection.getNetworkSettings().getNetworks().isEmpty()) {
                    ext.address = null
                    ext.gateway = null
                } else {
                    ContainerNetwork containerNetwork
                    if (networkName) {
                        containerNetwork = ext.inspection.getNetworkSettings().getNetworks().get(networkName)
                    } else {
                        for(final Map.Entry<String, ContainerNetwork> entry : ext.inspection.getNetworkSettings().getNetworks().entrySet()) {
                            if (!entry.getKey().equals('none')) {
                                containerNetwork = entry.getValue()
                                break
                            }
                        }
                    }
                    ext.address = containerNetwork?.getIpAddress()
                    ext.gateway = containerNetwork?.getGateway()
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
                logger.quiet "PORTS = " + ((ext.ports) ? ext.ports.collect { k, v -> "${v}->${k}" }.join(',') : ext.ports.toString())
                logger.quiet "ADDRESS = ${ext.address}"
                logger.quiet "GATEWAY = ${ext.gateway}"
                logger.quiet banner
            }
        }
    }
}
