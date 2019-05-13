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

import com.aries.gradle.docker.applications.plugin.domain.AbstractApplication
import com.aries.gradle.docker.applications.plugin.tasks.Down
import com.aries.gradle.docker.applications.plugin.tasks.Stop
import com.aries.gradle.docker.applications.plugin.tasks.Up
import com.bmuschko.gradle.docker.DockerRemoteApiPlugin
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.UnknownPluginException
import org.gradle.api.tasks.TaskProvider

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
            appContainer.initializeApplication()

            // create tasks after evaluation so that we can pick up any changes
            // made to our various extension points.
            final TaskProvider<Task> upTaskChain = createUpChain(project, appContainer)
            final TaskProvider<Task> stopTaskChain = createStopChain(project, appContainer)
            final TaskProvider<Task> downTaskChain = createDownChain(project, appContainer)
        }
    }

    private TaskProvider<Task> createUpChain(final Project project,
                                               final AbstractApplication appContainer) {

        final Collection<TaskProvider<Task>> taskChains = Up.createTaskChain(project, appContainer)
        final String appName = appContainer.getName()

        final TaskProvider<Task> upDependencies = project.tasks.register("${appName}Up_Dependencies") {
            onlyIf { appContainer.dependsOn() }
            outputs.upToDateWhen { false }

            dependsOn(appContainer.dependsOn())

            group: appName
            description: "Trigger all '${appName}' dependencies if not already triggered."
        }

        final TaskProvider<Task> upChain = project.tasks.register("${appName}Up_Chain") {
            outputs.upToDateWhen { false }

            mustRunAfter(upDependencies)
            dependsOn(taskChains)

            group: appName
            description: "Start all '${appName}' container application(s) if not already started."
        }

        return project.tasks.register("${appName}Up") {
            outputs.upToDateWhen { false }

            dependsOn(upChain, upDependencies)
            ext.applications = taskChains

            group: appName
            description: "Wrapper for starting all '${appName}' container application(s), and their dependencies, if not already done."
        }
    }

    private TaskProvider<Task> createStopChain(final Project project,
                                               final AbstractApplication appContainer) {

        final Collection<TaskProvider<Task>> taskChains = Stop.createTaskChain(project, appContainer)
        final String appName = appContainer.getName()

        return project.tasks.register("${appName}Stop") {
            outputs.upToDateWhen { false }

            dependsOn(taskChains)
            ext.applications = taskChains

            group: appName
            description: "Pause all '${appName}' container application(s) if not already paused."
        }
    }

    private TaskProvider<Task> createDownChain(final Project project,
                                               final AbstractApplication appContainer) {

        final Collection<TaskProvider<Task>> taskChains = Down.createTaskChain(project, appContainer)
        final String appName = appContainer.getName()

        return project.tasks.register("${appName}Down") {
            outputs.upToDateWhen { false }

            dependsOn(taskChains)
            ext.applications = taskChains

            group: appName
            description: "Delete all '${appName}' container application(s) if not already deleted."
        }
    }
}
