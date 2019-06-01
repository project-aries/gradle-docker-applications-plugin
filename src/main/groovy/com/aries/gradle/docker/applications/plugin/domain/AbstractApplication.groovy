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

import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
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

    // methods and properties used to configure the data container
    protected Options options
    final List<Closure<Options>> optionsConfigs = []
    void options(final Closure<Options> optionConfig) {
        if (optionConfig) {
            optionsConfigs.add(optionConfig)
        }
    }
    Options options() {
        options
    }

    // methods and properties used to configure the main container.
    protected MainContainer mainContainer
    final List<Closure<MainContainer>> mainContainerConfigs = []
    void main(final Closure<MainContainer> mainContainerConfig) {
        if (mainContainerConfig) {
            mainContainerConfigs.add(mainContainerConfig)
        }
    }
    MainContainer main() {
        mainContainer
    }
    String mainId() {
        options().id() ?: getName()
    }

    // methods and properties used to configure the data container
    protected DataContainer dataContainer
    final List<Closure<DataContainer>> dataContainerConfigs = []
    void data(final Closure<DataContainer> dataContainerConfig) {
        if (dataContainerConfig) {
            dataContainerConfigs.add(dataContainerConfig)
        }
    }
    DataContainer data() {
        dataContainer
    }
    String dataId() {
        "${mainId()}-data"
    }

    // internal method to check that our main and data containers are setup properly
    protected AbstractApplication initializeApplication() {

        // 1.) initialize options.
        options = new Options()
        for (final Closure<Options> cnf : this.optionsConfigs) {
            options = ConfigureUtil.configure(cnf, options)
        }

        // 2.) `main`, and all its properties, are required to be set and defined.
        mainContainer = new MainContainer()
        for (final Closure<MainContainer> cnf : mainContainerConfigs) {
            mainContainer = ConfigureUtil.configure(cnf, mainContainer)
        }

        Objects.requireNonNull(mainContainer.repository(), "'main' must have a valid repository defined")

        // 3.) `data` is not required to be defined as it will/can inherit properties from main.
        dataContainer = new DataContainer()
        for (final Closure<DataContainer> cnf : dataContainerConfigs) {
            dataContainer = ConfigureUtil.configure(cnf, dataContainer)
        }

        if (!dataContainer.repository()) {
            dataContainer.repository = mainContainer.repository()
            dataContainer.tag = mainContainer.tag()
        }

        this
    }
}
