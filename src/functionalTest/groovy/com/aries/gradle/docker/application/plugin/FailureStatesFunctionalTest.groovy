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
import org.gradle.testkit.runner.BuildResult
import spock.lang.Timeout

import static java.util.concurrent.TimeUnit.MINUTES

/**
 *
 *  Functional tests to force failures under certain scenarios.
 *
 */
class FailureStatesFunctionalTest extends AbstractFunctionalTest {

    @Timeout(value = 5, unit = MINUTES)
    def "Will fail if no main container is defined"() {

        String uuid = randomString()
        buildFile << """

            applications {
                someStack {
                    id = "${uuid}"
                }
            }
            
            task up(dependsOn: ['someStackUp'])
        """

        when:
            BuildResult result = buildAndFail('up')

        then:
            result.output.contains('container has not been defined')
    }

    @Timeout(value = 5, unit = MINUTES)
    def "Will fail if main container is defined but no repository is set"() {

        String uuid = randomString()
        buildFile << """

            applications {
                someStack {
                    id = "${uuid}"
                    main {
                    
                    }
                }
            }
            
            task up(dependsOn: ['someStackUp'])
        """

        when:
        BuildResult result = buildAndFail('up')

        then:
        result.output.contains('must have a valid repository defined')
    }

    @Timeout(value = 5, unit = MINUTES)
    def "Will fail if container image does not exist"() {

        String uuid = randomString()
        buildFile << """

            applications {
                someStack {
                    id = "${uuid}"
                    main {
                        repository = 'postgres'
                        tag = 'idontexist'
                    }
                }
            }
            
            task up(dependsOn: ['someStackUp'])
        """

        when:
        BuildResult result = buildAndFail('up')

        then:
        result.output.contains('were not found locally: pull required')
        result.output.contains('was not found remotely')
    }
}
