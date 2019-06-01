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

import com.bmuschko.gradle.docker.tasks.container.DockerCopyFileToContainer
import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional

/**
 *
 *  Options for containers
 *
 */
class Options {

    // if set will pass along a network name to use (will create custom network if not present) for application.
    @Input
    @Optional
    String network
    String network() {
        return Boolean.valueOf(skipNetwork()).booleanValue() ? null : (this.network ?: mainId())
    }

    // if set to true we will skip custom network creation and/or connecting to.
    @Input
    @Optional
    boolean skipNetwork = false
    String skipNetwork() {
        this.skipNetwork
    }

    // if set to true we will skip custom network creation and/or connecting to.
    @Input
    @Optional
    int count = 1
    int count() {
        this.count <= 0 ? 1 : this.count
    }

    // if set will override the application-name part of the docker container.
    @Input
    @Optional
    String id
    String id() {
        this.id
    }

    // if set will be used as a shared acquire amongst all tasks.
    @Input
    @Optional
    String lock
    String lock() {
        this.lock
    }

    // internal helper collection to hold AbstractApplication names
    // that this AbstractApplication dependsOn.
    @Internal
    final Collection<String> applicationDependsOn = ArrayList.newInstance()

    // if set will be used as a shared acquire amongst all tasks.
    @Input
    @Optional
    Collection<Object> dependsOn
    Collection<Object> dependsOn(Object... paths) {
        if (this.dependsOn == null) {
            this.dependsOn = ArrayList.newInstance()
        }

        if (paths) {
            paths.each { dep ->
                if (dep) {
                    def arbitraryDep = dep
                    if (arbitraryDep instanceof AbstractApplication) {
                        this.applicationDependsOn.add(dep.getName())
                        arbitraryDep = dep.getName() + "Up"
                    }
                    this.dependsOn.add(arbitraryDep)
                }
            }
        }

        this.dependsOn
    }
}
