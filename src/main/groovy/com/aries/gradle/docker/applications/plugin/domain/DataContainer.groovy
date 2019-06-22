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

import javax.annotation.Nullable

/**
 *
 *  Represents the `data` container of this application.
 *
 */
class DataContainer extends AbstractContainer {

    static DataContainer buildFrom(@Nullable final MainContainer mainContainer,
                                   @Nullable final Closure<DataContainer> dataConfig) {

        buildFrom(mainContainer, new ArrayList(dataConfig))
    }

    static DataContainer buildFrom(@Nullable final MainContainer mainContainer,
                                   @Nullable final List<Closure<DataContainer>> dataConfigs) {

        final DataContainer dataContainer = new DataContainer()
        if (dataConfigs) {
            for(final Closure<DataContainer> cnf : dataConfigs) {
                if (cnf != null) {
                    ConfigureUtil.configure(cnf, dataContainer)
                }
            }
        }

        if (!dataContainer.repository()) {
            if (mainContainer) {
                dataContainer.repository = mainContainer.repository()
                dataContainer.tag = mainContainer.tag()
            } else {
                throw new IllegalArgumentException("MainContainer must be defined if no closure/config object(s) are passed.")
            }
        }

        return dataContainer
    }
}
