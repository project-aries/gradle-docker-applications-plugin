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

import org.gradle.api.Project
import org.gradle.internal.logging.progress.ProgressLoggerFactory

/**
 *
 * Place to house project wide static methods.
 *
 */
class GradleDockerDatabasesPluginUtils {

    /**
     * Random String prepended with, by default, the token `gddp-`
     * which is shorthand for `gradle-docker-databases-plugin`.
     */
    static String randomString(def prependWith = 'gddp-') {
        prependWith + UUID.randomUUID().toString().replaceAll("-", "")
    }

    static def createProgressLogger(final Project project, final Class clazz){
        def serviceFactory = project.gradle.getServices()
        def progressLoggerFactory = serviceFactory.get(ProgressLoggerFactory)
        def progressLogger = progressLoggerFactory.newOperation(clazz)
        progressLogger.setDescription("Progress logger for ${clazz.getSimpleName()}")
        progressLogger.setLoggingHeader(null)
    }

    private GradleDockerDatabasesPluginUtils() {
        throw new RuntimeException('Purposefully not implemented')
    }
}
