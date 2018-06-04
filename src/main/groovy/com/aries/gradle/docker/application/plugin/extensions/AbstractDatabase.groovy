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

package com.aries.gradle.docker.application.plugin.extensions

import com.aries.gradle.docker.application.plugin.common.ImageInfo

import org.gradle.api.tasks.Optional
import org.gradle.util.ConfigureUtil

/**
 *
 *  Base class for all databases to inherit common functionality from.
 *
 */
public abstract class AbstractDatabase {

    @Optional
    String id
    String id() {
        this.id ?: System.getProperty("user.name")
    }

    // upon start/restart we will query the main containers logs for this
    // message, if null no attempt to will be made to query
    @Optional
    String liveOnLog
    String liveOnLog() {
        this.liveOnLog ?: null
    }

    // methods and properties used to configure the main container
    protected ImageInfo main
    void main(Closure<ImageInfo> info) {
        main = ConfigureUtil.configure(info, main ?: new ImageInfo())
    }
    String mainId() {
        "${id()}-${this.mainImage().repository().split('/').last()}"
    }
    ImageInfo mainImage() {
        this.main
    }

    // methods and properties used to configure the data container
    protected ImageInfo data
    void data(Closure<ImageInfo> info) {
        data = ConfigureUtil.configure(info, data ?: new ImageInfo())
    }
    String dataId() {
        "${mainId()}-data"
    }
    ImageInfo dataImage() {
        this.data ?: mainImage()
    }
}
