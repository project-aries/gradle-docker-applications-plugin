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

import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer

/**
 *
 *  Base class for all containers to inherit functionality from.
 *
 */
class AbstractContainer {

    // not required to be set and will be lazily checked at runtime for null
    public String repository
    String repository() {
        this.repository
    }

    public String tag
    String tag() {
        this.tag = this.tag ?: 'latest'
    }

    String image() {
        "${this.repository()}:${this.tag()}"
    }

    final List<Closure<DockerCreateContainer>> createConfigs = []
    void create(Closure<DockerCreateContainer> createConfig) {
        if (createConfig) { createConfigs.add(createConfig) }
    }
}
