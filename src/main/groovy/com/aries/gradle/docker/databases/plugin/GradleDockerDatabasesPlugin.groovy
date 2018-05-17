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

    public static final String DEFAULT_TASK_GROUP = 'DockerDatabases'
    public static final List<Class> DATABASES = [Db2, Oracle, Postgres, Sqlserver].asImmutable()

    @Override
    void apply(final Project project) {

        // 1.) create all database extension points
        createDatabaseExtensionPoints(project)

        // 2.) create all database tasks
        createDatabaseTasks(project)
    }

    /*
     * Create our various database extension points.
     */
    private createDatabaseExtensionPoints(final Project project) {
        project.extensions.create(Databases.simpleName.toLowerCase, Databases)
        DATABASES.each {
            project.extensions.create(it.simpleName.toLowerCase, it)
        }
    }

    /*
     *  Create common tasks for all databases
     */
    private createDatabaseTasks(final Project project) {
        
    }
}
