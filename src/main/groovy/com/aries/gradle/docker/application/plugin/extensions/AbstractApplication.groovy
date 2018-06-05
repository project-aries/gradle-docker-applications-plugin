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

import com.aries.gradle.docker.application.plugin.common.AbstractContainer

import org.gradle.api.tasks.Optional
import org.gradle.util.ConfigureUtil

import com.bmuschko.gradle.docker.tasks.container.extras.DockerExecStopContainer

/**
 *
 *  Base class for all applications to inherit common functionality from.
 *
 */
public class AbstractApplication {

    final String name
    AbstractApplication(final String name) {
        this.name = name
    }

    @Optional
    String id
    String id() {
        this.id ?: System.getProperty('user.name')
    }

    // upon start/restart we will query the main containers logs for this
    // message, if null no attempt to will be made to query
    @Optional
    String liveOnLog
    String liveOnLog() {
        this.liveOnLog ?: null
    }

    // methods and properties used to configure the main container
    protected AbstractContainer main
    void main(final Closure<AbstractContainer> info) {
        main = ConfigureUtil.configure(info, main ?: new AbstractContainer())
    }
    AbstractContainer main() {
        this.main
    }
    String mainId() {
        "${id()}-${this.main().repository().split('/').last()}"
    }

    // methods and properties used to configure the data container
    protected AbstractContainer data
    void data(final Closure<AbstractContainer> info) {
        data = ConfigureUtil.configure(info, data ?: new AbstractContainer())
    }
    AbstractContainer data() {
        this.data ?: main()
    }
    String dataId() {
        "${mainId()}-data"
    }

    final List<Closure<DockerExecStopContainer>> stops = []
    void stop(Closure<DockerExecStopContainer> stop) {
        if (stop) { stops.add(stop) }
    }
}
