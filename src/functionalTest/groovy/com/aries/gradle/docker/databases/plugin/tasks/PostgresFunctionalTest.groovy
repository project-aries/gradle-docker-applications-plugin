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

package com.aries.gradle.docker.databases.plugin.tasks

import static java.util.concurrent.TimeUnit.SECONDS

import com.aries.gradle.docker.databases.plugin.AbstractFunctionalTest
import org.gradle.testkit.runner.BuildResult
import spock.lang.Timeout

/**
 *
 *  Functional tests for the `postgres` tasks.
 *
 */
class PostgresFunctionalTest extends AbstractFunctionalTest {

    @Timeout(value = 60, unit = SECONDS)
    def "Can standup, stop and then shutdown postgres stack"() {

        def String uuid = randomString()
        buildFile << """

            databases {
                id = "bears"
            }

            postgres {
                id = "bears"
            }
            
            task up(dependsOn: ['PostgresUp'])
            
            task stop(dependsOn: ['PostgresStop'])

            task down(dependsOn: ['PostgresDown'])
        """

        when:
            BuildResult result = build('up', 'down')

        then:
            result.output.contains('Pulling repository') || result.output.contains(':PostgresPullImage SKIPPED')
            result.output.contains('fish8')
            !result.output.contains(':PostgresListImages SKIPPED')
    }
}
