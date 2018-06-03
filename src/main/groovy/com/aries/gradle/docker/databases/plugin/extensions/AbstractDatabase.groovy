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

package com.aries.gradle.docker.databases.plugin.extensions

import com.aries.gradle.docker.databases.plugin.common.ImageInfo
import org.gradle.api.tasks.Optional
import org.gradle.util.ConfigureUtil

/**
 *
 *
 *  Base class for all databases to inherit common functionality from.
 *
 */
public abstract class AbstractDatabase {

    @Optional
    String id

    private ImageInfo main
    private ImageInfo data

    String containerRepository // docker repository portion of containerImage (e.g. postgres) for main container

    String containerTag // docker tag id (e.g. latest or 10.0) for main container

    @Optional
    String containerDataRepository // docker repository portion of containerImage (e.g. postgres) for data container

    @Optional
    String containerDataTag // docker tag id (e.g. latest or 10.0) for data container

    @Optional
    String liveOnLog // the log line within the container we will use to confirm it is "live"

    void main(Closure<ImageInfo> info) {
        main = ConfigureUtil.configure(info, main ?: new ImageInfo())
    }

    void data(Closure<ImageInfo> info) {
        data = ConfigureUtil.configure(info, data ?: new ImageInfo())
    }

    abstract String containerRepository()

    abstract String defaultPort()

    abstract String liveOnLog()

    String id() {
        this.id ?: System.getProperty("user.name")
    }

    String containerId() {
        "${id()}-application"
    }

    String containerDataId() {
        "${containerId()}-data"
    }

    String containerTag() {
        this.containerTag ?: 'latest'
    }

    String containerDataTag() {
        this.containerDataTag ?: 'latest'
    }

    String containerImage() {
        "${containerRepository()}:${containerTag()}"
    }

    String containerDataImage() {
        "${containerDataRepository()}:${containerDataTag()}"
    }
}
