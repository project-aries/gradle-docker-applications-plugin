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

package com.aries.gradle.docker.application.plugin.tasks

import org.gradle.testkit.runner.BuildResult

/**
 *
 *  Functional tests for the `db2` tasks.
 *
 */
class Db2FunctionalTest extends com.aries.gradle.docker.application.plugin.AbstractFunctionalTest {

    def "Can pull a Db2 image"() {
        buildFile << """

            task pullImage(dependsOn: ['Db2PullImage'])

            task workflow(dependsOn: pullImage)
        """

        when:
            BuildResult result = build('workflow')

        then:
            result.output.contains('Pulling mainRepository') || result.output.contains(':Db2PullImage SKIPPED')
    }
}