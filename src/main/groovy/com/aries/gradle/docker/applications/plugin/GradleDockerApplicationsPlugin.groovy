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

package com.aries.gradle.docker.applications.plugin

import com.aries.gradle.docker.applications.plugin.tasks.Down
import com.aries.gradle.docker.applications.plugin.tasks.Stop
import com.aries.gradle.docker.applications.plugin.tasks.Up
import com.bmuschko.gradle.docker.tasks.network.DockerCreateNetwork
import com.bmuschko.gradle.docker.tasks.network.DockerInspectNetwork
import com.bmuschko.gradle.docker.tasks.network.DockerRemoveNetwork
import com.github.dockerjava.api.model.ContainerNetwork
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider

import static GradleDockerApplicationsPluginUtils.randomString
import static GradleDockerApplicationsPluginUtils.throwOnValidError
import static GradleDockerApplicationsPluginUtils.throwOnValidErrorElseGradleException

import static com.bmuschko.gradle.docker.utils.IOUtils.getProgressLogger

import com.aries.gradle.docker.applications.plugin.domain.AbstractApplication

import com.bmuschko.gradle.docker.DockerRemoteApiPlugin
import com.bmuschko.gradle.docker.tasks.container.DockerCopyFileToContainer
import com.bmuschko.gradle.docker.tasks.container.DockerExecContainer
import com.bmuschko.gradle.docker.tasks.container.extras.DockerExecStopContainer
import com.bmuschko.gradle.docker.tasks.container.extras.DockerLivenessContainer
import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
import com.bmuschko.gradle.docker.tasks.container.DockerInspectContainer
import com.bmuschko.gradle.docker.tasks.container.DockerRemoveContainer
import com.bmuschko.gradle.docker.tasks.container.DockerRestartContainer
import com.bmuschko.gradle.docker.tasks.container.DockerStartContainer
import com.bmuschko.gradle.docker.tasks.image.DockerListImages
import com.bmuschko.gradle.docker.tasks.image.DockerPullImage
import com.bmuschko.gradle.docker.tasks.DockerOperation

import org.gradle.api.GradleException
import org.gradle.api.plugins.UnknownPluginException
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.StopExecutionException

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 *  Plugin providing common tasks for starting (*Up), stopping (*Stop), and deleting (*Down) dockerized applications.
 */
class GradleDockerApplicationsPlugin implements Plugin<Project> {

    public static final String EXTENSION_NAME = 'applications'

    @Override
    void apply(final Project project) {

        // 1.) apply required plugins
        try {
            project.plugins.apply('com.bmuschko.docker-remote-api')
        } catch (UnknownPluginException upe) {
            project.plugins.apply(DockerRemoteApiPlugin)
        }

        // 2.) build domain-container for housing ad-hoc applications
        final NamedDomainObjectContainer<AbstractApplication> appContainers = project.container(AbstractApplication)

        // 3.) build plugin extension point from domain-container
        project.extensions.add(EXTENSION_NAME, appContainers)

        project.afterEvaluate {

            // 4.) create all application tasks
            createApplicationTasks(project, appContainers)
        }
    }

    /*
     *  Create domain tasks for all applications
     */
    private createApplicationTasks(final Project project,
                                   final NamedDomainObjectContainer<AbstractApplication> appContainers) {

        appContainers.each { appContainer ->

            // Must be run after evaluation has happened but prior to tasks
            // being built. This ensures our main and data container were
            // properly setup and in the case of the latter we will inherit
            // its properties from the former if it wasn't defined.
            appContainer.sanityCheck()

            // create tasks after evaluation so that we can pick up any changes
            // made to our various extension points.
            Up.createTaskChain(project, appContainer)
            Stop.createTaskChain(project, appContainer)
            Down.createTaskChain(project, appContainer)
        }
    }
}
