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
import org.gradle.internal.logging.progress.ProgressLogger
import org.gradle.internal.service.ServiceRegistry

import javax.annotation.Nullable

/**
 *
 * Place to house project wide static methods.
 *
 */
class GradleDockerDatabasesPluginUtils {

    /**
     * Get a random string prepended with some identifer.
     *
     * @param prependWith optional string to prepend to generated random string. Defaults to `gddp-` if null or not specified.
     * @return
     */
    static String randomString(def prependWith = 'gddp-') {
        prependWith + UUID.randomUUID().toString().replaceAll("-", "")
    }

    /**
     * Create a progress logger for an arbitrary project and class.
     *
     * @param project the project to create a ProgressLogger for.
     * @param clazz optional class to pair the ProgressLogger to. Defaults to _this_ class if null.
     * @return instance of ProgressLogger.
     */
    static ProgressLogger createProgressLogger(final Project project, final Class clazz = GradleDockerDatabasesPluginUtils){
        final ServiceRegistry serviceFactory = project.gradle.getServices()
        final ProgressLoggerFactory progressLoggerFactory = serviceFactory.get(ProgressLoggerFactory)
        final ProgressLogger progressLogger = progressLoggerFactory.newOperation(Objects.requireNonNull(clazz))
        progressLogger.setDescription("ProgressLogger for ${clazz.getSimpleName()}")
        progressLogger.setLoggingHeader(null)
    }

    private GradleDockerDatabasesPluginUtils() {
        throw new RuntimeException('Purposefully not implemented')
    }
}
