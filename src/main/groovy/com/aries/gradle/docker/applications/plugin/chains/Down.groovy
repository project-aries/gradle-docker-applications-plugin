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

package com.aries.gradle.docker.applications.plugin.chains

import com.aries.gradle.docker.applications.plugin.domain.AbstractApplication
import com.aries.gradle.docker.applications.plugin.tasks.DockerManageContainer
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider

/**
 *  Contains single static method to create the `Down` task chain.
 */
final class Down {

    private Down() {
        throw new UnsupportedOperationException("Purposefully not implemented.")
    }

    static Collection<TaskProvider<Task>> createTaskChain(final Project project,
                                                    final AbstractApplication appContainer) {

        final Collection<TaskProvider<Task>> taskList = ArrayList.newInstance()

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

        final TaskProvider<DockerManageContainer> dataContainer = tasks.register("${appName}_Data_Down" + appender, DockerManageContainer) {

            group: appName
            description: "Delete '${appName}' data container if not already deleted."

            id = dataId
            command = 'down'
            network = networkName

            lock {
                name = lockName
                lock = true
            }
        }

        final TaskProvider<DockerManageContainer> mainContainer = tasks.register("${appName}_Main_Down" + appender, DockerManageContainer) {

            dependsOn(dataContainer)

            group: appName
            description: "Delete '${appName}' main container if not already deleted."

            id = mainId
            command = 'down'
            network = networkName

            lock {
                name = lockName
                unlock = true
            }
        }

        return mainContainer
    }
}
