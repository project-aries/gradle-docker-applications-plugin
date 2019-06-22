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

package com.aries.gradle.docker.applications.plugin.utils

import com.aries.gradle.docker.applications.plugin.domain.Pair
import com.aries.gradle.docker.applications.plugin.domain.Proxy
import com.aries.gradle.docker.applications.plugin.report.SummaryReport

import javax.annotation.Nullable
import java.util.concurrent.atomic.AtomicInteger

import static com.aries.gradle.docker.applications.plugin.GradleDockerApplicationsPluginUtils.randomString
import static com.aries.gradle.docker.applications.plugin.GradleDockerApplicationsPluginUtils.resource
import static com.aries.gradle.docker.applications.plugin.GradleDockerApplicationsPlugin.pluginProject

/**
 *
 *  Static utility methods to deal with nginx.
 *
 */
class NginxUtils {

    // ports exposed from WITHIN the nginx container and nowhere else
    private static final AtomicInteger RANDOM_PORT_GENERATOR = new AtomicInteger(81)

    private NginxUtils() {
        throw new RuntimeException('Purposefully not implemented')
    }

    static Pair<String, List<String>> buildConfig(final List<Proxy> proxies,
                                                  final List<SummaryReport> reports) {

        if (proxies) {
            if (reports) {

                final List<String> requestedBindings = new ArrayList<>()
                final List<Integer> requestedPorts = new ArrayList<>()
                final StringBuilder upstreamServers = new StringBuilder()
                for(final Proxy pro : proxies) {

                    final String upstreamName = randomString(null)
                    final StringBuilder upServe = new StringBuilder("\tupstream ${upstreamName} {${System.lineSeparator()}")
                    if (pro.method) { upServe.append("\t\t${pro.method}${System.lineSeparator()}") }

                    boolean sumAdded = false
                    for(final SummaryReport sum : reports) {
                        String exposedPort = sum.ports.get(String.valueOf(pro.toPort))
                        if (exposedPort) {
                            upServe.append("\t\tserver ${sum.gateway}:${exposedPort};${System.lineSeparator()}")
                            sumAdded = true
                        }
                    }

                    if (sumAdded) {

                        int listenPort = sanitize(pro.fromPort)
                        if (listenPort == 0) {
                            listenPort = getRandomPort(requestedPorts)
                            requestedBindings.add(":${listenPort}")
                        } else {
                            requestedBindings.add("${listenPort}:${listenPort}")
                        }
                        requestedPorts.add(listenPort)

                        upServe.append("\t}${System.lineSeparator()}")
                        upServe.append("\tserver {${System.lineSeparator()}")
                        upServe.append("\t\tlisten ${listenPort};${System.lineSeparator()}")
                        upServe.append("\t\tlocation / {${System.lineSeparator()}")
                        upServe.append("\t\t\tproxy_pass http://${upstreamName};${System.lineSeparator()}")
                        upServe.append("\t\t\tproxy_redirect off;${System.lineSeparator()}")
                        upServe.append("\t\t\tproxy_set_header Host \$host;${System.lineSeparator()}")
                        upServe.append("\t\t\tproxy_set_header X-Real-IP \$remote_addr;${System.lineSeparator()}")
                        upServe.append("\t\t\tproxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;${System.lineSeparator()}")
                        upServe.append("\t\t\tproxy_set_header X-Forwarded-Host \$server_name;${System.lineSeparator()}")
                        upServe.append("\t\t}${System.lineSeparator()}")
                        upServe.append("\t}${System.lineSeparator()}")

                        upstreamServers.append(upServe.toString())
                    } else {
                        pluginProject.logger.quiet("No apps were listening on ${pro.toPort}. Skipping front-end server definition...")
                    }
                }

                if (upstreamServers.length() > 0) {
                    final String content = resource("/nginx.conf").replace("#UPSTREAM_SERVERS#", upstreamServers.toString())
                    return Pair.of(content, requestedBindings)
                } else {
                    pluginProject.logger.quiet("No upstream's were generated. Skipping front-end...")
                }
            } else {
                pluginProject.logger.quiet("No SummaryReport's defined. Skipping front-end...")
            }
        } else {
            pluginProject.logger.quiet("No Proxy configs defined. Skipping front-end...")
        }

        return Pair.ofNullable()
    }

    static synchronized int getRandomPort(@Nullable final List<Integer> excludePorts) {
        if (excludePorts) {
            while (true) {
                int nextNumber = RANDOM_PORT_GENERATOR.incrementAndGet()
                if (!excludePorts.contains(nextNumber)) {
                    return nextNumber
                }
            }
        } else {
            RANDOM_PORT_GENERATOR.incrementAndGet()
        }
    }

    static Integer sanitize(Integer numToSanitize) {
        return (numToSanitize == null || numToSanitize < 0) ? 0 : numToSanitize
    }
}
