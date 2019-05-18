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
 *  Functional tests to execute DockerManageContainer.
 *
 */
class DockerManageContainerFunctionalTest extends AbstractFunctionalTest {

    @Timeout(value = 5, unit = MINUTES)
    def "Execute DockerManageContainer"() {

        String uuid = "devops"
        buildFile << """

            task runC(type: com.aries.gradle.docker.applications.plugin.tasks.DockerManageContainer) {
                id = "${uuid}"
                repository = 'postgres'
                tag = 'alpine'
                network = "${uuid}"
                
                command = 'down'
                
                doFirst {
                    println 'In the KICKER'
                }
                
                create {
                    withEnvVar("CI", "TRUE")
                    withEnvVar("DEVOPS", "ROCKS")
                    portBindings = [':5432']
                    cmd = ['postgres', '-c', 'shared_buffers=256MB', '-c', 'max_connections=200']
                }
                liveness {
                    livenessProbe(300000, 10000, 'database system is ready to accept connections')
                }
                
                stop {
                    withCommand(['su', 'postgres', "-c", "/usr/local/bin/pg_ctl stop -m fast"])
                    successOnExitCodes = [0, 127, 137]
                    awaitStatusTimeout = 60000
                    execStopProbe(60000, 10000)
                }
            }
                   
        """

        when:
            BuildResult result = build('runC')

        then:
            result.output.contains('In the KICKER')
    }
}
