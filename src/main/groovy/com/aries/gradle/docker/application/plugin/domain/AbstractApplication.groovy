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

import org.gradle.api.tasks.Optional
import org.gradle.util.ConfigureUtil

/**
 *
 *  Base class for all applications to inherit functionality from.
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

    // methods and properties used to configure the main container
    protected MainContainer main
    void main(final Closure<MainContainer> info) {
        main = ConfigureUtil.configure(info, main ?: new MainContainer())
    }
    MainContainer main() {
        this.main ?: new MainContainer()
    }
    String mainId() {
        "${id()}-${this.main().repository().split('/').last()}"
    }

    // methods and properties used to configure the data container
    protected DataContainer data
    void data(final Closure<DataContainer> info) {
        data = ConfigureUtil.configure(info, data ?: new DataContainer())
    }
    DataContainer data() {
        this.data ?: new DataContainer()
    }
    String dataId() {
        "${mainId()}-data"
    }
}
