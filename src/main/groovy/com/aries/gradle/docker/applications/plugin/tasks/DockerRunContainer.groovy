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

import com.bmuschko.gradle.docker.tasks.DockerOperation
import com.bmuschko.gradle.docker.tasks.container.DockerCopyFileToContainer
import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
import com.bmuschko.gradle.docker.tasks.container.DockerExecContainer
import com.bmuschko.gradle.docker.tasks.container.DockerInspectContainer
import com.bmuschko.gradle.docker.tasks.container.DockerRemoveContainer
import com.bmuschko.gradle.docker.tasks.container.DockerRestartContainer
import com.bmuschko.gradle.docker.tasks.container.DockerStartContainer
import com.bmuschko.gradle.docker.tasks.container.extras.DockerLivenessContainer
import com.bmuschko.gradle.docker.tasks.image.DockerInspectImage
import com.bmuschko.gradle.docker.tasks.image.DockerPullImage
import com.bmuschko.gradle.docker.tasks.network.DockerCreateNetwork
import com.bmuschko.gradle.docker.tasks.network.DockerInspectNetwork
import com.github.dockerjava.api.model.ContainerNetwork
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Task
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider

import static com.aries.gradle.docker.applications.plugin.GradleDockerApplicationsPluginUtils.applyConfigs
import static com.aries.gradle.docker.applications.plugin.GradleDockerApplicationsPluginUtils.executeTask
import static com.aries.gradle.docker.applications.plugin.GradleDockerApplicationsPluginUtils.randomString
import static com.aries.gradle.docker.applications.plugin.GradleDockerApplicationsPluginUtils.throwOnValidError

/**
 *
 */
class DockerRunContainer extends DefaultTask {

    @Input
    final Property<String> id = project.objects.property(String)

    @Input
    final Property<String> repository = project.objects.property(String)

    @Input
    final Property<String> tag = project.objects.property(String)

    @Input
    @Optional
    final Property<String> network = project.objects.property(String)

    @Input
    @Optional
    final ListProperty<Closure<DockerCreateContainer>> createContainerConfigs = project.objects.listProperty(Closure)

    @Input
    @Optional
    final ListProperty<Closure<DockerCopyFileToContainer>> copyFileConfigs = project.objects.listProperty(Closure)

    @Input
    @Optional
    final ListProperty<Closure<DockerLivenessContainer>> livenessConfigs = project.objects.listProperty(Closure)

    @Input
    @Optional
    final ListProperty<Closure<DockerExecContainer>> execConfigs = project.objects.listProperty(Closure)

    DockerRunContainer() {
        createContainerConfigs.empty()
        copyFileConfigs.empty()
        livenessConfigs.empty()
        execConfigs.empty()
    }

    @TaskAction
    void execute() {

        final String containerId = id.get()
        final String repositoryId = repository.get()
        final String tagId = tag.get()
        final String imageId = repositoryId + ":" + tagId
        final String networkName = network.getOrNull()


        // 1.) Check if container is currently available
        final Task availableContainerTask = project.tasks.create(randomString(), DockerInspectContainer, {
            targetContainerId(containerId)
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
            }
        })
        executeTask(availableContainerTask)

        // 2.) Create network if requested and not present
        if (networkName && !availableContainerTask.ext.hasNetwork) {
            final Task inspectNetworkTask = project.tasks.create(randomString(), DockerInspectNetwork, {
                ext.hasNetwork = true
                networkId = networkName
                onError {
                    ext.hasNetwork = false
                }
            })
            executeTask(inspectNetworkTask)

            if (!inspectNetworkTask.ext.hasNetwork) {
                final Task createNetworkTask = project.tasks.create(randomString(), DockerCreateNetwork, {
                    networkId = networkName
                })
                executeTask(createNetworkTask)
            }
        }

        // 3.) Restart container but only if it's available and not in a running state
        boolean restartedContainer = false
        Date restartDate = null
        if (availableContainerTask.ext.exists == true && availableContainerTask.ext.inspection.state.running == false) {

            final Task restartContainerTask = project.tasks.create(randomString(), DockerRestartContainer, {
                restartDate = new Date()
                targetContainerId(containerId)
                waitTime = 3000
            })
            executeTask(restartContainerTask)
            restartedContainer = true
        }

        // 4.) Pull image for container if it does not already exist
        if (availableContainerTask.ext.exists == false) {

            final Task inspectImageTask = project.tasks.create(randomString(), DockerInspectImage, {
                targetImageId(imageId)
                ext.hasImage = false
                onNext {
                    ext.hasImage = true
                }
                onError { err ->
                    throwOnValidError(err)
                }
            })
            executeTask(inspectImageTask)

            if (inspectImageTask.ext.hasImage == false) {
                final Task pullImageTask = project.tasks.create(randomString(), DockerPullImage, { cnf ->
                    cnf.repository = repositoryId
                    cnf.tag = tagId
                    cnf.onError { err ->
                        throwOnValidError(err)
                    }
                })
                executeTask(pullImageTask)
            }
        }

        // create container if it doesn't exist
        if(availableContainerTask.ext.exists == false) {

            final Task createContainerTask = project.tasks.create(randomString(), DockerCreateContainer, { cnf ->
                cnf.network = networkName
                cnf.targetImageId(imageId)
                cnf.containerName = containerId
                //cnf.volumesFrom = [dataId]
            })
            applyConfigs(createContainerTask, createContainerConfigs.get())
            executeTask(createContainerTask)

            if (copyFileConfigs.get()) {
                final Task copyFilesToContainerTask = project.tasks.create(randomString(), DockerCopyFileToContainer, {
                    targetContainerId(containerId)
                })
                applyConfigs(copyFilesToContainerTask, copyFileConfigs.get())
                executeTask(copyFilesToContainerTask)
            }

            final Task startContainerTask = project.tasks.create(randomString(), DockerStartContainer, {
                ext.startTime = new Date()
                targetContainerId(containerId)
            })
            executeTask(startContainerTask)
        }

        // pause done to allow the container to come up and potentially
        // exit (e.g. container that has no entrypoint or cmd defined).
        sleep(2000)
        final Task livenessContainerTask = project.tasks.create(randomString(), DockerLivenessContainer, {

            targetContainerId(containerId)

            // only 2 ways this task can kick so we will proceed to configure
            // the `since` option based ONLY upon a "restart" scenario as we will
            // use it to determine where in the logs we should start from whereas
            // in the "start" scenario we simply start from the very beginning
            // of the logs docker gives us.
            since.set( restartDate ?: null )
            onComplete {

                // though we should be live at this point we sleep for
                // another 2 seconds to give the potential application
                // some breathing room before we start hammering away
                // on it with potential requests.
                sleep(2000)
            }
        })
        applyConfigs(livenessContainerTask, livenessConfigs.get())
        executeTask(livenessContainerTask)

        boolean execStarted = false
        if (execConfigs.get() && (restartedContainer == false)) {
            final Task execContainerTask = project.tasks.create(randomString(), DockerExecContainer, {

                targetContainerId(containerId)

                onComplete {

                    // sleeping for 2 seconds just in-case any command caused this container to
                    // come down, potentially gracefully, before we presume things are live.
                    sleep(2000)
                }
            })
            applyConfigs(execContainerTask, execConfigs.get())
            executeTask(execContainerTask)
            execStarted = true
        }

        final Task summaryContainerTask = project.tasks.create(randomString(), DockerOperation, {

            onNext { dockerClient ->

                // 1.) Set the last used "inspection" for potential downstream use
                if (execStarted) {

                    // if the `execContainerTask` task kicked we need to
                    // make an additional inspection call to ensure things
                    // are still live and running just to be on the safe side.
                    ext.inspection = dockerClient.inspectContainerCmd(containerId).exec()
                    if (!ext.inspection.state.running) {
                        throw new GradleException("Container '${containerId}' was NOT in a running state after exec(s) finished. Was this expected?")
                    }
                } else {
                    ext.inspection = livenessContainerTask.lastInspection()
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

                logger.quiet '' // newline just to put a break between last output and this banner being printed
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
        })
        executeTask(summaryContainerTask)
    }

    void create(final Closure<DockerCreateContainer> createContainerConfig) {
        if (createContainerConfig) {
            createContainerConfigs.add(createContainerConfig)
        }
    }

    void files(final Closure<DockerCopyFileToContainer> copyFileConfig) {
        if (copyFileConfig) {
            copyFileConfigs.add(copyFileConfig)
        }
    }

    void liveness(final Closure<DockerLivenessContainer > livenessConfig) {
        if (livenessConfig) {
            livenessConfigs.add(livenessConfig)
        }
    }

    void exec(final Closure<DockerExecContainer > execConfig) {
        if (execConfig) {
            execConfigs.add(execConfig)
        }
    }
}
