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

package com.aries.gradle.docker.application.plugin.domain

import com.bmuschko.gradle.docker.tasks.container.DockerStartContainer
import com.bmuschko.gradle.docker.tasks.container.extras.DockerExecStopContainer
import com.bmuschko.gradle.docker.tasks.container.extras.DockerLivenessProbeContainer

import com.aries.gradle.docker.application.plugin.domain.common.AbstractContainer

/**
 *
 *  Represents the `main` container of this application.
 *
 */
class MainContainer extends AbstractContainer {

    // Supply X number of closures to further configure the
    // `DockerLivenessProbeContainer` task which is responsible
    // for potentially polling the previously started container
    // for a given log message to ensure its liveness.
    final List<Closure<DockerLivenessProbeContainer>> livenessConfigs = []
    void liveness(Closure<DockerLivenessProbeContainer> livenessConfig) {
        if (livenessConfig) { livenessConfigs.add(livenessConfig) }
    }

    // Supply X number of closures to further configure the
    // `DockerStartContainer` task which is responsible for
    // starting the container.
    final List<Closure<DockerStartContainer>> startConfigs = []
    void start(Closure<DockerStartContainer> startConfig) {
        if (startConfig) { startConfigs.add(startConfig) }
    }

    // Supply X number of closures to further configure the
    // `DockerExecStopContainer` task which is responsible for
    // stopping the container gracefully, via an exec command
    // from within the container, and if that fails then stop
    // the container in the old fashioned way.
    final List<Closure<DockerExecStopContainer>> stopConfigs = []
    void stop(Closure<DockerExecStopContainer> stopConfig) {
        if (stopConfig) { stopConfigs.add(stopConfig) }
    }
}
