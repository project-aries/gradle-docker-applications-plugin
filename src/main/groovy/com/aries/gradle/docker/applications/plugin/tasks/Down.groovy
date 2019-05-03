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

import com.aries.gradle.docker.applications.plugin.GradleDockerApplicationsPluginUtils
import com.aries.gradle.docker.applications.plugin.domain.AbstractApplication
import com.bmuschko.gradle.docker.tasks.container.DockerRemoveContainer
import com.bmuschko.gradle.docker.tasks.network.DockerRemoveNetwork
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider

import static com.aries.gradle.docker.applications.plugin.GradleDockerApplicationsPluginUtils.buildAcquireExecutionLockTask
import static com.aries.gradle.docker.applications.plugin.GradleDockerApplicationsPluginUtils.buildReleaseExecutionLockTask

/**
 *  Contains single static method to create the `Down` task chain.
 */
final class Down {

    private Down() {
        throw new UnsupportedOperationException("Purposefully not implemented.")
    }

    // create required tasks for invoking the "down" chain.
    private static TaskProvider<Task> createTaskChain(final Project project,
                                              final AbstractApplication appContainer) {

        final TaskContainer tasks = project.tasks;

        final String appName = appContainer.getName()
        final String appGroup = appContainer.mainId()
        final String networkName = appContainer.network()

        final TaskProvider<Task> acquireExecutionLockTask = buildAcquireExecutionLockTask(project, appName, appGroup)
        final TaskProvider<Task> releaseExecutionLockTask = buildReleaseExecutionLockTask(project, appName, appGroup)

        final TaskProvider<DockerRemoveContainer> deleteContainerTask = tasks.register("${appName}DeleteContainer", DockerRemoveContainer) {

            dependsOn(acquireExecutionLockTask)

            group: appGroup
            description: "Delete '${appName}' container."

            removeVolumes = true
            force = true
            targetContainerId { appContainer.mainId() }

            onError { err ->
                GradleDockerApplicationsPluginUtils.throwOnValidError(err)
                logger.quiet "Container with ID '${appContainer.mainId()}' is not available to delete."
            }
        }

        final TaskProvider<DockerRemoveContainer> deleteDataContainerTask = tasks.register("${appName}DeleteDataContainer", DockerRemoveContainer) {

            dependsOn(deleteContainerTask)

            group: appGroup
            description: "Delete '${appName}' data container."

            removeVolumes = true
            force = true
            targetContainerId { appContainer.dataId() }

            onError { err ->
                GradleDockerApplicationsPluginUtils.throwOnValidError(err)
                logger.quiet "Container with ID '${appContainer.dataId()}' is not available to delete."
            }
        }

        final TaskProvider<DockerRemoveNetwork> removeNetworkTask = tasks.register("${appName}RemoveNetwork", DockerRemoveNetwork) {
            onlyIf { networkName }

            dependsOn(deleteDataContainerTask)

            group: appGroup
            description: "Remove '${appName}' network."

            targetNetworkId { appContainer.mainId() }

            onError { err ->
                GradleDockerApplicationsPluginUtils.throwOnValidError(err)
                logger.quiet "Network with ID '${appContainer.mainId()}' is not available to remove."
            }
        }

        return project.tasks.register("${appName}Down") {
            outputs.upToDateWhen { false }

            dependsOn(removeNetworkTask)
            finalizedBy(releaseExecutionLockTask)

            group: appGroup
            description: "Delete '${appName}' container application if not already deleted."
        }
    }
}
