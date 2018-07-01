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

        new File("$projectDir/HelloWorld.txt").withWriter('UTF-8') {
            it.write('Hello, World!')
        }

        String uuid = randomString()
        buildFile << """

            applications {
                myPostgresStack {
                    id = "${uuid}"
                    main {
                        repository = 'postgres'
                        tag = 'alpine'
                        create {
                            env = ['CI=TRUE', 'DEVOPS=ROCKS']
                        }
                        files {
                            withFile("$projectDir/HelloWorld.txt", '/') // demo with strings
                            withFile( { "$projectDir/HelloWorld.txt" }, { '/tmp' }) // demo with closures
                        }
                        stop {
                            cmd = ['su', 'postgres', "-c", "/usr/local/bin/pg_ctl stop -m fast"]
                            successOnExitCodes = [0, 127, 137]
                            timeout = 60000
                            execStopProbe(60000, 10000)
                        }
                        liveness {
                            livenessProbe(300000, 10000, 'database system is ready to accept connections')
                        }
                        exec {
                            withCommand(['su', 'postgres', "-c", "/usr/local/bin/pg_ctl status"])
                            successOnExitCodes = [0]
                        }
                    }
                    data {
                        create {
                            volumes = ["/var/lib/postgresql/data"]
                        }
                        files {
                            withFile(project.file("$projectDir/HelloWorld.txt"), project.file('/root')) // demo with files
                        }
                    }
                }
            }
            
            task up(dependsOn: ['myPostgresStackUp']) {
                doLast {
                    logger.quiet 'FOUND INSPECTION: ' + myPostgresStackUp.ext.inspection
                }
            }
            
            task stop(dependsOn: ['myPostgresStackStop'])

            task down(dependsOn: ['myPostgresStackDown'])
        """

        when:
            BuildResult result = build('up', 'stop', 'down')

        then:
            result.output.contains('is not running or available to inspect')
            result.output.contains('Inspecting container with ID')
            result.output.contains('Created container with ID')
            count(result.output, 'Copying file to container') == 3
            result.output.contains('Copying file to container')
            result.output.contains('Starting liveness')
            result.output.contains('CI=TRUE')
            result.output.contains('DEVOPS=ROCKS')
            result.output.contains('Running exec-stop on container with ID')
            result.output.contains('Removing container with ID')
            result.output.contains('RestartContainer SKIPPED')
            !result.output.contains('ListImages SKIPPED')
    }
}
