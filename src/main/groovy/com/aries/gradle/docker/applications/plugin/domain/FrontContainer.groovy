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

import static com.aries.gradle.docker.applications.plugin.GradleDockerApplicationsPlugin.pluginProject

import org.gradle.api.tasks.Internal
import org.gradle.util.ConfigureUtil

/**
 *
 *  Represents the `frontend` container of this application.
 *
 */
class FrontContainer extends BaseContainer {

    @Internal
    private final List<Closure<Proxy>> proxyConfigs = []

    FrontContainer() {
        this.repository = 'nginx'
        this.tag = '1.17.0-alpine'
    }

    void proxy(final List<Closure<Proxy>> proxyConfigList) {
        if (proxyConfigList) {
            for(final Closure<Proxy> pro : proxyConfigList) {
                proxy(pro)
            }
        }
    }

    void proxy(final Closure<Proxy> proxyConfig) {
        if (proxyConfig) { proxyConfigs.add(proxyConfig) }
    }

    List<Proxy> proxies() {
        final List<Proxy> runtimeProxies = new ArrayList<>()
        for(final Closure<Proxy> pro : proxyConfigs) {
            if (pro != null) {
                final Proxy proxy = new Proxy()
                ConfigureUtil.configure(pro, proxy)
                if (proxy.toPort != null) {
                    runtimeProxies.add(proxy)
                } else {
                    pluginProject.logger.quiet("Proxy did NOT have a 'to' port set. Will ignore...")
                }
            }
        }
        Collections.sort(runtimeProxies)
        runtimeProxies
    }

    static FrontContainer buildFrom(final List<Closure<FrontContainer>> frontConfigs) {
        if (frontConfigs && frontConfigs.size() > 0) {
            final FrontContainer frontContainer = new FrontContainer()
            for(final Closure<FrontContainer> cnf : frontConfigs) {
                if (cnf != null) {
                    ConfigureUtil.configure(cnf, frontContainer)
                }
            }

            return frontContainer
        } else {
            return null
        }
    }
}
