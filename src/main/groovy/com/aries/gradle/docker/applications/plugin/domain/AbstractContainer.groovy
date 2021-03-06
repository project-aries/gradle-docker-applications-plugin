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

/**
 *
 *  Base class for all dockerized container applications to inherit functionality from.
 *
 */
class AbstractContainer extends BaseContainer {

    final List<Closure<DockerCreateContainer>> createConfigs = []
    void create(Closure<DockerCreateContainer> createConfig) {
        if (createConfig) { createConfigs.add(createConfig) }
    }

    final List<Closure<DockerCopyFileToContainer>> filesConfigs = []
    void files(Closure<DockerCopyFileToContainer> filesConfig) {
        if (filesConfig) { filesConfigs.add(filesConfig) }
    }
}
