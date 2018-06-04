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

package com.aries.gradle.docker.application.plugin

import org.gradle.api.Project
import org.gradle.internal.logging.progress.ProgressLoggerFactory
import org.gradle.internal.logging.progress.ProgressLogger
import org.gradle.internal.service.ServiceRegistry

/**
 *
 * Place to house project wide static methods.
 *
 */
class GradleDockerApplicationPluginUtils {

    /**
     * Get a random string prepended with some identifer.
     *
     * @param prependWith optional string to prepend to generated random string.
     * @return
     */
    static String randomString(def prependWith = 'gdap-') {
        prependWith + UUID.randomUUID().toString().replaceAll("-", "")
    }

    private GradleDockerApplicationPluginUtils() {
        throw new RuntimeException('Purposefully not implemented')
    }
}
