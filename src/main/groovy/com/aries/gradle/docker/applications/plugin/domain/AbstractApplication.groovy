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

import org.gradle.util.ConfigureUtil

/**
 *
 *  Base class for all applications to inherit functionality from.
 *
 */
class AbstractApplication {

    final String name
    AbstractApplication(final String name) {
        this.name = name
    }

    // if set will override the application-name part of the docker container
    String id
    String id() {
        this.id
    }

    // methods and properties used to configure the main container
    protected MainContainer main
    void main(final Closure<MainContainer> info) {
        this.main = ConfigureUtil.configure(info, this.main ?: new MainContainer())
    }
    MainContainer main() {
        Objects.requireNonNull(this.main, "The 'main' container has not been defined.")
    }
    String mainId() {
        id() ?: getName()
    }

    // methods and properties used to configure the data container
    protected DataContainer data
    void data(final Closure<DataContainer> info) {
        data = ConfigureUtil.configure(info, data ?: new DataContainer())
    }
    DataContainer data() {
        this.data = this.data ?: new DataContainer()
    }
    String dataId() {
        "${mainId()}-data"
    }

    // internal method to check that our main and data containers are setup properly
    protected void sanityCheck() {

        // 1.) `main`, and all its properties, are required to be set and defined.
        final MainContainer mainCheck = main()
        Objects.requireNonNull(mainCheck.repository(), "'main' must have a valid repository defined")

        // 2.) `data` is not required to be defined as it will/can inherit properties from main.
        final DataContainer dataCheck = data()
        if (!dataCheck.repository()) {
            dataCheck.repository = mainCheck.repository()
            dataCheck.tag = mainCheck.tag()
        }
    }
}
