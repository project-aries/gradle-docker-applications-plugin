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

package com.aries.gradle.docker.application.plugin

import com.aries.gradle.docker.application.plugin.AbstractFunctionalTest

import static java.util.concurrent.TimeUnit.MINUTES

import org.gradle.testkit.runner.BuildResult
import spock.lang.Timeout

/**
 *
 *  Functional tests to perform up, pause, and down tasks.
 *
 */
class EndToEndFunctionalTest extends AbstractFunctionalTest {

    @Timeout(value = 5, unit = MINUTES)
    def "Can start, stop, and remove a postgres application stack"() {

        String uuid = randomString()
        buildFile << """

            applications {
                postgres {
                    id = 'bears'
                    main {
                        repository = 'postgres'
                        tag = 'alpine'
                        stop {
                            cmd = ['su', 'postgres', "-c", "/usr/local/bin/pg_ctl stop -m fast"]
                            successOnExitCodes = [0, 127, 137]
                            timeout = 60000
                            probe(60000, 10000)
                        }
                        liveness {
                            probe(300000, 10000, 'database system is ready to accept connections')
                        }
                    }
                }
            }
            
            task up(dependsOn: ['postgresUp'])
            
            task stop(dependsOn: ['postgresStop'])

            task down(dependsOn: ['postgresDown'])
        """

        when:
            BuildResult result = build('up', 'stop', 'down')

        then:
            result.output.contains('Pulling mainRepository') || result.output.contains(':postgresPullImage SKIPPED')
            result.output.contains('fish8')
            !result.output.contains(':postgresListImages SKIPPED')
    }
}
