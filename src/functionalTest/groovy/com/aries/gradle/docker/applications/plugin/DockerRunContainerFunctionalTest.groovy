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

package com.aries.gradle.docker.applications.plugin

import org.gradle.testkit.runner.BuildResult
import spock.lang.Timeout

import static java.util.concurrent.TimeUnit.MINUTES

/**
 *
 *  Functional tests to execute DockerRunContainer.
 *
 */
class DockerRunContainerFunctionalTest extends AbstractFunctionalTest {

    @Timeout(value = 5, unit = MINUTES)
    def "Execute DockerRunContainer"() {

        String uuid = "devops"
        //String uuid = randomString()
        buildFile << """

            task runC(type: com.aries.gradle.docker.applications.plugin.tasks.DockerRunContainer) {
                id = "${uuid}"
                repository = 'postgres'
                tag = 'alpine'
                network = "${uuid}"
                create {
                    withEnvVar("CI", "TRUE")
                    withEnvVar("DEVOPS", "ROCKS")
                    portBindings = [':5432']
                    cmd = ['postgres', '-c', 'shared_buffers=256MB', '-c', 'max_connections=200']
                }
                liveness {
                    livenessProbe(300000, 10000, 'database system is ready to accept connections')
                }
            }
                   
        """

        when:
            BuildResult result = build('runC')

        then:
            result.output.contains('In the KICKER')
    }
}
