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
import com.bmuschko.gradle.docker.tasks.container.DockerRemoveContainer
import com.bmuschko.gradle.docker.tasks.network.DockerRemoveNetwork
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider

import static com.aries.gradle.docker.applications.plugin.GradleDockerApplicationsPluginUtils.buildAcquireExecutionLockTask
import static com.aries.gradle.docker.applications.plugin.GradleDockerApplicationsPluginUtils.buildReleaseExecutionLockTask
import static com.aries.gradle.docker.applications.plugin.GradleDockerApplicationsPluginUtils.throwOnValidError

/**
 *  Contains single static method to create the `Down` task chain.
 */
final class Down {

    private Down() {
        throw new UnsupportedOperationException("Purposefully not implemented.")
    }

    static List<TaskProvider<Task>> createTaskChain(final Project project,
                                                    final AbstractApplication appContainer) {

        final List<TaskProvider<Task>> taskList = new ArrayList();

        for (int i = 0; i < appContainer.count(); i++) {
            final TaskProvider<Task> singleTaskChain = _createTaskChain(project, appContainer, "_" + (i + 1))
            taskList.add(singleTaskChain)
        }

        return taskList
    }

    // create required tasks for invoking the "down" chain.
    private static TaskProvider<Task> _createTaskChain(final Project project,
                                                       final AbstractApplication appContainer,
                                                       final String appender) {

        final String dataId = appContainer.dataId() + appender
        final String mainId = appContainer.mainId() + appender
        final String appName = appContainer.getName()
        final String lockName = appContainer.lock() ?: mainId
        final String networkName = appContainer.network()

        final TaskContainer tasks = project.tasks;
        final TaskProvider<Task> acquireExecutionLockTask = buildAcquireExecutionLockTask(project, appName, lockName)
        final TaskProvider<Task> releaseExecutionLockTask = buildReleaseExecutionLockTask(project, appName, lockName)

        String taskName = "${appName}DeleteContainer" + appender
        final TaskProvider<DockerRemoveContainer> deleteContainerTask = tasks.register(taskName, DockerRemoveContainer) {

            dependsOn(acquireExecutionLockTask)

            group: appName
            description: "Delete '${appName}' container."

            removeVolumes = true
            force = true
            targetContainerId { mainId }

            onError { err ->
                throwOnValidError(err)
                logger.quiet "Container with ID '${mainId}' is not available to delete."
            }
        }

        taskName = "${appName}DeleteDataContainer" + appender
        final TaskProvider<DockerRemoveContainer> deleteDataContainerTask = tasks.register(taskName, DockerRemoveContainer) {

            dependsOn(deleteContainerTask)

            group: appName
            description: "Delete '${appName}' data container."

            removeVolumes = true
            force = true
            targetContainerId { dataId }

            onError { err ->
                throwOnValidError(err)
                logger.quiet "Container with ID '${dataId}' is not available to delete."
            }
        }

        taskName = "${appName}RemoveNetwork" + appender
        final TaskProvider<DockerRemoveNetwork> removeNetworkTask = tasks.register(taskName, DockerRemoveNetwork) {
            onlyIf { networkName }

            dependsOn(deleteDataContainerTask)

            group: appName
            description: "Remove '${networkName}' network."

            targetNetworkId { networkName }

            onError { err ->
                throwOnValidError(err)
                logger.quiet "Network with ID '${networkName}' is not available to remove."
            }
        }

        taskName = "${appName}Down" + appender
        return project.tasks.register(taskName) {
            outputs.upToDateWhen { false }

            dependsOn(removeNetworkTask)
            finalizedBy(releaseExecutionLockTask)

            group: appName
            description: "Delete '${appName}' container application if not already deleted."
        }
    }
}
