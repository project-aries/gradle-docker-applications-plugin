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

package com.aries.gradle.docker.applications.plugin.domain

import com.bmuschko.gradle.docker.tasks.container.DockerExecContainer
import com.bmuschko.gradle.docker.tasks.container.extras.DockerExecStopContainer
import com.bmuschko.gradle.docker.tasks.container.extras.DockerLivenessContainer
import org.gradle.util.ConfigureUtil

import static java.util.Objects.requireNonNull

/**
 *
 *  Represents the `main` container of this application.
 *
 */
class MainContainer extends AbstractContainer {

    // Supply X number of closures to further configure the
    // `DockerLivenessContainer` task which is responsible
    // for potentially polling the previously started container
    // for a given log message to ensure its liveness.
    final List<Closure<DockerLivenessContainer>> livenessConfigs = []
    void liveness(Closure<DockerLivenessContainer> livenessConfig) {
        if (livenessConfig) { livenessConfigs.add(livenessConfig) }
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

    // Supply X number of closures to further configure the
    // `DockerExecContainer` task which is responsible for
    // executing an arbitrary number of commands inside the
    // `main` container but ONLY AFTER it has successfully
    // become live.
    final List<Closure<DockerExecContainer>> execConfigs = []
    void exec(Closure<DockerExecStopContainer> execConfig) {
        if (execConfig) { execConfigs.add(execConfig) }
    }

    static MainContainer buildFrom(final Closure<MainContainer>... mainConfig) {
        final List<Closure<MainContainer>> mainConfigs = new ArrayList<>(mainConfig as List)
        buildFrom(mainConfigs)
    }

    static MainContainer buildFrom(final List<Closure<MainContainer>> mainConfigs) {

        if (!mainConfigs) {
            throw new IllegalArgumentException("Must pass at least 1 closure/config object to buildFrom MainContainer.")
        } else {
            final MainContainer mainContainer = new MainContainer()
            for(final Closure<MainContainer> cnf : mainConfigs) {
                if (cnf != null) {
                    ConfigureUtil.configure(cnf, mainContainer)
                }
            }

            requireNonNull(mainContainer.repository(), "'main' must have a valid repository defined")
            return mainContainer
        }
    }
}
