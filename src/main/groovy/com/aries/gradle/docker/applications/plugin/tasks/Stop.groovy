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
import com.bmuschko.gradle.docker.tasks.container.extras.DockerExecStopContainer
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider

import static com.aries.gradle.docker.applications.plugin.GradleDockerApplicationsPluginUtils.buildAcquireExecutionLockTask
import static com.aries.gradle.docker.applications.plugin.GradleDockerApplicationsPluginUtils.buildReleaseExecutionLockTask
import static com.aries.gradle.docker.applications.plugin.GradleDockerApplicationsPluginUtils.applyConfigs

/**
 *  Contains single static method to create the `Stop` task chain.
 */
final class Stop {

    private Stop() {
        throw new UnsupportedOperationException("Purposefully not implemented.")
    }

    // create required tasks for invoking the "stop" chain.
    static TaskProvider<Task> createTaskChain(final Project project,
                                   final AbstractApplication appContainer) {

        final TaskContainer tasks = project.tasks;

        final String appName = appContainer.getName()
        final String appGroup = appContainer.mainId()

        final TaskProvider<Task> acquireExecutionLockTask = buildAcquireExecutionLockTask(project, appName, appGroup)
        final TaskProvider<Task> releaseExecutionLockTask = buildReleaseExecutionLockTask(project, appName, appGroup)

        final TaskProvider<DockerExecStopContainer> execStopContainerTask = tasks.register("${appName}ExecStopContainer", DockerExecStopContainer) {

            dependsOn(acquireExecutionLockTask)

            group: appGroup
            description: "Stop '${appName}' container."

            targetContainerId { appContainer.mainId() }
            onNext { output ->
                // pipe output to nowhere for the time being
            }
            onError { err ->
                GradleDockerApplicationsPluginUtils.throwOnValidError(err)
                logger.quiet "Container with ID '${appContainer.mainId()}' is not running or available to stop."
            }
        }
        applyConfigs(execStopContainerTask, appContainer.main().stopConfigs)

        return tasks.register("${appName}Stop") {
            outputs.upToDateWhen { false }

            dependsOn(execStopContainerTask)
            finalizedBy(releaseExecutionLockTask)

            group: appGroup
            description: "Stop '${appName}' container application if not already paused."
        }
    }
}
