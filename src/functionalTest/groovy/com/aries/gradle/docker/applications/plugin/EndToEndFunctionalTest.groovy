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

        // splitting things up into multiple files only to show that the user
        // can add as many files as they want.
        final File createDatabase = new File("$projectDir/CREATE_DATABASE.sql")
        createDatabase.withWriter('UTF-8') {
            it.println('CREATE DATABASE docker;')
            createDatabase
        }

        final File createUser = new File("$projectDir/CREATE_DATABASE_USER.sql")
        createUser.withWriter('UTF-8') {
            it.println('CREATE USER docker;')
            createUser
        }

        final File grantPrivileges = new File("$projectDir/CREATE_DATABASE_USER_PRIVS.sql")
        grantPrivileges.withWriter('UTF-8') {
            it.println('GRANT ALL PRIVILEGES ON DATABASE docker TO docker;')
            grantPrivileges
        }

        final File createCookie = new File("$projectDir/cookie.txt")
        createCookie.withWriter('UTF-8') {
            it.println('Somebody Was Here')
            createCookie
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
                            envVars << ['CI' : 'TRUE', 'DEVOPS' : 'ROCKS']
                            portBindings = [':5432']
                        }
                        files {
                            withFile("${createDatabase.path}", '/docker-entrypoint-initdb.d') // demo with strings
                            withFile( { "${createUser.path}" }, { '/docker-entrypoint-initdb.d' }) // demo with closures
                            withFile(project.file("${grantPrivileges.path}"), '/docker-entrypoint-initdb.d') // mixed demo with files
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
                        
                            // can define as many of these commands as you'd like
                            // which will kick on initial **UP** only.
                            withCommand(['su', 'postgres', "-c", "/usr/local/bin/psql -c 'CREATE DATABASE devops'"])
                            withCommand(['su', 'postgres', "-c", "/usr/local/bin/psql -c 'CREATE USER devops'"])
                            withCommand(['su', 'postgres', "-c", "/usr/local/bin/psql -c 'GRANT ALL PRIVILEGES ON DATABASE devops TO devops'"])

                            withCommand(['su', 'postgres', "-c", "/usr/local/bin/pg_ctl status"])
                            successOnExitCodes = [0]
                        }
                    }
                    data {
                        create {
                            volumes = ["/var/lib/postgresql/data"]
                        }
                        files {
                            withFile(project.file("$projectDir/cookie.txt"), project.file('/')) // demo with files
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
            result.output.contains('PullDataImage SKIPPED')
            result.output.contains('Created container with ID')
            count(result.output, 'Copying file to container') == 4
            result.output.contains('Copying file to container')
            result.output.contains('Starting liveness')
            result.output.contains('CI=TRUE')
            result.output.contains('DEVOPS=ROCKS')
            result.output.contains('Running exec-stop on container with ID')
            result.output.contains('CREATE DATABASE')
            result.output.contains('CREATE ROLE')
            result.output.contains('GRANT')
            result.output.contains('pg_ctl: server is running')
            result.output.contains('Removing container with ID')
            result.output.contains('RestartContainer SKIPPED')
            !result.output.contains('ListImages SKIPPED')
    }
}
