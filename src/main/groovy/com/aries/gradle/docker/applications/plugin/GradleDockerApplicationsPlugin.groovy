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
import com.aries.gradle.docker.applications.plugin.domain.CommandTypes
import com.aries.gradle.docker.applications.plugin.tasks.DockerManageContainer
import com.bmuschko.gradle.docker.DockerRemoteApiPlugin
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.UnknownPluginException
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.TaskProvider

/**
 *
 *  Plugin providing common tasks for starting (*Up), stopping (*Stop), and deleting (*Down) dockerized applications.
 *
 */
class GradleDockerApplicationsPlugin implements Plugin<Project> {

    public static final String EXTENSION_NAME = 'applications'

    // setting/exposing here because injecting these into POJO's is currently
    // not working as of 6/4/19 or maybe I'm just doing something wrong. IDK.
    public static ObjectFactory objectFactory
    public static ProviderFactory providerFactory

    private static final Set<String> EMPTY_SET = Collections.unmodifiableSet( new HashSet<String>() );

    public static final String UP = CommandTypes.UP.type()
    public static final String STOP = CommandTypes.STOP.type()
    public static final String DOWN = CommandTypes.DOWN.type()

    @Override
    void apply(final Project project) {

        objectFactory = project.objects
        providerFactory = project.providers

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

            // create tasks after evaluation so that we can pick up any changes
            // made to our various extension points.
            final TaskProvider<Task> upTaskChain = createUpChain(project, appContainer)
            final TaskProvider<Task> stopTaskChain = createStopChain(project, appContainer)
            final TaskProvider<Task> downTaskChain = createDownChain(project, appContainer)
        }
    }

    private TaskProvider<Task> createUpChain(final Project project,
                                             final AbstractApplication appContainer) {

        final String appName = appContainer.getName()

        final TaskProvider<DockerManageContainer> upApp = project.tasks.register("${appName}Up_App", DockerManageContainer, {

            dependsOn(appContainer.dependsOn, appContainer.dependsOnApp.get(UP, EMPTY_SET))

            command = UP
            count = appContainer.count.getOrElse(1)
            id = appContainer.id.getOrNull() ?: appName

            network = project.provider {
                String networkName = appContainer.network.getOrNull()
                if (networkName && networkName.equals('generate')) {
                    networkName = appContainer.id.getOrNull() ?: appName
                }
                networkName
            }

            main(appContainer.mainConfigs)
            data(appContainer.dataConfigs)

            group: appName
            description: "Start '${appName}' if not already started."
        })

        return project.tasks.register("${appName}${UP}") {
            outputs.upToDateWhen { false }

            doFirst { ext.reports = upApp.get().reports() }
            dependsOn(upApp, appContainer.dependsOnParallel, appContainer.dependsOnParallelApp.get(UP, EMPTY_SET))

            group: appName
            description: "Start all '${appName}' container application(s), and their dependencies, if not already started."
        }
    }

    private TaskProvider<Task> createStopChain(final Project project,
                                               final AbstractApplication appContainer) {

        final String appName = appContainer.getName()

        final TaskProvider<Task> stopApp = project.tasks.register("${appName}Stop_App", DockerManageContainer, {

            dependsOn(appContainer.dependsOnApp.get(STOP, EMPTY_SET))

            command = STOP
            count = appContainer.count.getOrElse(1)
            id = appContainer.id.getOrNull() ?: appName
            main(appContainer.mainConfigs)
            data(appContainer.dataConfigs)

            group: appName
            description: "Stop '${appName}' if not already stopped."
        })

        return project.tasks.register("${appName}${STOP}") {
            outputs.upToDateWhen { false }

            dependsOn(stopApp, appContainer.dependsOnParallelApp.get(STOP, EMPTY_SET))

            group: appName
            description: "Wrapper for stopping all '${appName}' container application(s) if not already stopped."
        }
    }

    private TaskProvider<Task> createDownChain(final Project project,
                                               final AbstractApplication appContainer) {

        final String appName = appContainer.getName()

        final TaskProvider<Task> downApp = project.tasks.register("${appName}Down_App", DockerManageContainer, {

            dependsOn(appContainer.dependsOnApp.get(DOWN, EMPTY_SET))

            command = DOWN
            count = appContainer.count.getOrElse(1)
            id = appContainer.id.getOrNull() ?: appName

            network = project.provider {
                String networkName = appContainer.network.getOrNull()
                if (networkName && networkName.equals('generate')) {
                    networkName = appContainer.id.getOrNull() ?: appName
                }
                networkName
            }

            main(appContainer.mainConfigs)
            data(appContainer.dataConfigs)

            group: appName
            description: "Delete '${appName}' if not already deleted."
        })

        return project.tasks.register("${appName}${DOWN}") {
            outputs.upToDateWhen { false }

            dependsOn(downApp, appContainer.dependsOnParallelApp.get(DOWN, EMPTY_SET))

            group: appName
            description: "Wrapper for deleting all '${appName}' container application(s), and their dependencies, if not already deleted."
        }
    }
}
