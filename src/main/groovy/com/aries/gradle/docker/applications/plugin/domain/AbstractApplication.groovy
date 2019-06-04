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

import com.aries.gradle.docker.applications.plugin.GradleDockerApplicationsPlugin
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.util.ConfigureUtil

import javax.inject.Inject

/**
 *
 *  Base class for all applications to inherit functionality from.
 *
 */
class AbstractApplication {

    @Internal
    private ObjectFactory objectFactory = GradleDockerApplicationsPlugin.objectFactory

    // if set will pass along a network name to use (will create custom network if not present) for application.
    @Input
    @Optional
    final Property<String> network = objectFactory.property(String)

    // if set to true we will skip custom network creation and/or connecting to.
    @Input
    @Optional
    final Property<Boolean> skipNetwork = objectFactory.property(Boolean)

    // if set to true we will skip custom network creation and/or connecting to.
    @Input
    @Optional
    final Property<Integer> count = objectFactory.property(Integer)

    // if set will override the application-name part of the docker container.
    @Input
    @Optional
    final Property<String> id = objectFactory.property(String)

    // internal helper collection to hold AbstractApplication names
    // that this AbstractApplication dependsOn.
    @Internal
    final Collection<String> applicationDependsOn = ArrayList.newInstance()

    // if set will be used as a shared acquire amongst all tasks.
    @Input
    @Optional
    final Collection<Object> dependsOn = []
    Collection<Object> dependsOn(final Object... paths = null) {
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

        dependsOn
    }

    final String name
    AbstractApplication(final String name) {
        this.name = name
        this.skipNetwork.set(false)
        this.count.set(1)
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

    // internal method to check that our main and data containers are setup properly
    protected AbstractApplication initializeApplication() {

        // 1.) `main`, and all its properties, are required to be set and defined.
        mainContainer = new MainContainer()
        for (final Closure<MainContainer> cnf : mainContainerConfigs) {
            mainContainer = ConfigureUtil.configure(cnf, mainContainer)
        }

        Objects.requireNonNull(mainContainer.repository(), "'main' must have a valid repository defined")

        // 2.) `data` is not required to be defined as it will/can inherit properties from main.
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
