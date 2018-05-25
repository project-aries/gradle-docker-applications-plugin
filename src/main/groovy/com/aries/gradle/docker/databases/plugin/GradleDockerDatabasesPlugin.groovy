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

package com.aries.gradle.docker.databases.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.FileCollection

import com.aries.gradle.docker.databases.plugin.extensions.Databases
import com.aries.gradle.docker.databases.plugin.extensions.Db2
import com.aries.gradle.docker.databases.plugin.extensions.Oracle
import com.aries.gradle.docker.databases.plugin.extensions.Postgres
import com.aries.gradle.docker.databases.plugin.extensions.Sqlserver

/**
 *  Plugin providing common tasks for interacting with various dockerized databases.
 */
class GradleDockerDatabasesPlugin implements Plugin<Project> {

    public static final List<Class> DATABASES = [Db2, Oracle, Postgres, Sqlserver].asImmutable()

    @Override
    void apply(final Project project) {

        // 1.) apply required plugins
        project.plugins.apply('com.bmuschko.docker-remote-api')

        // 2.) create all database extension points
        createDatabaseExtensionPoints(project)

        // 3.) create all database tasks
        createDatabaseTasks(project)
    }

    /*
     * Create our various database extension points.
     */
    private createDatabaseExtensionPoints(final Project project) {
        project.extensions.create(Databases.simpleName.toLowerCase(), Databases)
        DATABASES.each { dbClass ->
            project.extensions.create(dbClass.simpleName.toLowerCase(), dbClass)
        }
    }

    /*
     *  Create common tasks for all databases
     */
    private createDatabaseTasks(final Project project) {
        DATABASES.each { dbClass ->

            // commmon variables used by all tasks below
            final String dbType = dbClass.simpleName
            final String dbGroup = "${dbType}-database"
            final def dbExtension = project.extensions.getByName(dbType.toLowerCase())

            final def inspectContainerTaskName = "${dbType}InspectContainer"
            project.task(inspectContainerTaskName,
                type: com.bmuschko.gradle.docker.tasks.container.DockerInspectContainer) {

                group: dbGroup
                description: 'Check if container is present and possibly running'
                containerId = dbExtension.databaseId()

                ext.exists = false
                ext.running = false
                onNext { possibleContainer ->
                    ext.exists = true
                    ext.running = possibleContainer.getState().getRunning()
                }
                onError { err ->
                    if (err.class.simpleName != 'NotFoundException') {
                        throw err
                    } else {
                        logger.quiet "Container with ID '${dbExtension.databaseId()}' is not present."
                    }
                }
            }

            final def listImagesTaskName = "${dbType}ListImages"
            project.task(listImagesTaskName,
                type: com.bmuschko.gradle.docker.tasks.image.DockerListImages,
                dependsOn: [inspectContainerTaskName]) {
                onlyIf { project.tasks.getByName(inspectContainerTaskName).ext.exists == false }

                group: dbGroup
                description: 'Check if database image exists locally'
                imageName = dbExtension.repository()

                // check if the image we need is already available so that we don't
                // have to pull it further below
                ext.imageAvailableLocally = false
                onNext { possibleImage ->
                    if (ext.imageAvailableLocally == false) {
                        possibleImage.repoTags.each { rep ->
                            if (ext.imageAvailableLocally == false && rep.first() == dbExtension.image()) {
                                logger.quiet "Image with ID '${dbExtension.image()}' was found locally. No need to pull."
                                ext.imageAvailableLocally = true
                            }
                        }
                    }
                }
            }

            final def pullImageTaskName = "${dbType}PullImage"
            project.task(pullImageTaskName,
                type: com.bmuschko.gradle.docker.tasks.image.DockerPullImage,
                dependsOn: [listImagesTaskName]) {
                onlyIf { project.tasks.getByName(listImagesTaskName).ext.imageAvailableLocally == false }

                group: dbGroup
                description: 'Pull database image'
                repository = dbExtension.repository()
                tag = dbExtension.tag()
            }

            final def upTaskName = "${dbType}Up"
            project.task(upTaskName) {
                group: dbGroup
                description: 'Start database container stack if not already started'
            }

            final def downTaskName = "${dbType}Down"
            project.task(downTaskName) {
                group: dbGroup
                description: 'Stop database container stack if not already stopped'
            }

            final def removeTaskName = "${dbType}Remove"
            project.task(removeTaskName) {
                group: dbGroup
                description: 'Remove database container stack if not already removed'
            }
        }
    }
}
