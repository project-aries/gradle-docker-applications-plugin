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

    @Timeout(value = 3, unit = MINUTES)
    def "Can start, stop, and remove a postgres application stack"() {

        final File mySpecialEntryPoint = new File("$projectDir/special-docker-entrypoint.sh")
        mySpecialEntryPoint.withWriter('UTF-8') {
            it.println('#!/usr/bin/env bash')
            it.println('echo My Special Hello World')
            it.println('docker-entrypoint.sh "$@"')
            mySpecialEntryPoint
        }
        mySpecialEntryPoint.setExecutable(true);

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

        buildFile << """

            configurations {
                dev
            }

            dependencies {
                dev 'org.ajoberstar:gradle-git:1.7.2' // random dep just to get the point across
            }

            task kicker {
                doLast {
                    println 'In the KICKER' // random task just to get the point across
                }
            }

            def sharedNetworkName = 'postgres-stack'
            def sharedGroupName = 'postgres-stack'

            applications {

                myPostgresStackDep {
                    network(sharedNetworkName)
                    count(1)
                    group(sharedGroupName)
                    dependsOn(configurations.dev, kicker)
                    main {
                        repository = 'postgres'
                        tag = 'alpine'
                        create {
                            withEnvVar("CI", "TRUE")
                            withEnvVar("DEVOPS", "ROCKS")
                            portBindings = [':5432']
                            entrypoint = ["/${mySpecialEntryPoint.getName()}"]
                            cmd = ['postgres', '-c', 'shared_buffers=256MB', '-c', 'max_connections=200']
                        }
                        files {
                            withFile("${mySpecialEntryPoint.path}", '/') // demo with strings
                            withFile("${createDatabase.path}", '/docker-entrypoint-initdb.d') // demo with strings
                            withFile( { "${createUser.path}" }, { '/docker-entrypoint-initdb.d' }) // demo with closures
                            withFile(project.file("${grantPrivileges.path}"), '/docker-entrypoint-initdb.d') // mixed demo with files
                        }
                        stop {
                            withCommand(['su', 'postgres', "-c", "/usr/local/bin/pg_ctl stop -m fast"])
                            successOnExitCodes = [0, 127, 137]
                            awaitStatusTimeout = 60000
                            execStopProbe(60000, 10000)
                        }
                        liveness {
                            livenessProbe(300000, 10000, 'database system is ready to accept connections')
                        }
                        exec {
                        
                            // can define as many of these commands as you'd like
                            // which will kick on initial **UP** only.
                            withCommand(['su', 'postgres', "-c", "/usr/local/bin/psql -c 'CREATE DATABASE devops'"])
                            withCommand(['sleep', '5'])
                            withCommand(['su', 'postgres', "-c", "/usr/local/bin/psql -c 'CREATE USER devops'"])
                            withCommand(['sleep', '5'])
                            withCommand(['su', 'postgres', "-c", "/usr/local/bin/psql -c 'GRANT ALL PRIVILEGES ON DATABASE devops TO devops'"])
                            
                            // sleeping as below calls can fail while the above is still catching up
                            withCommand(['sleep', '5'])
                            withCommand(['su', 'postgres', "-c", "/usr/local/bin/psql -c 'SELECT * FROM pg_settings'"])
                            withCommand(['su', 'postgres', "-c", "/usr/local/bin/pg_ctl status"])
                            withCommand(['printenv'])
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
                
                def applicationDep = getByName('myPostgresStackDep')
                
                myPostgresStack {
                    network(sharedNetworkName)
                    count(3)
                    group(sharedGroupName)
                    dependsOnParallel(applicationDep)
                    dependsOn(configurations.dev, kicker)
                    main {
                        repository = 'postgres'
                        tag = 'alpine'
                        create {
                            withEnvVar("CI", "TRUE")
                            withEnvVar("DEVOPS", "ROCKS")
                            portBindings = [':5432']
                            entrypoint = ["/${mySpecialEntryPoint.getName()}"]
                            cmd = ['postgres', '-c', 'shared_buffers=256MB', '-c', 'max_connections=200']
                        }
                        files {
                            withFile("${mySpecialEntryPoint.path}", '/') // demo with strings
                            withFile("${createDatabase.path}", '/docker-entrypoint-initdb.d') // demo with strings
                            withFile( { "${createUser.path}" }, { '/docker-entrypoint-initdb.d' }) // demo with closures
                            withFile(project.file("${grantPrivileges.path}"), '/docker-entrypoint-initdb.d') // mixed demo with files
                        }
                        stop {
                            withCommand(['su', 'postgres', "-c", "/usr/local/bin/pg_ctl stop -m fast"])
                            successOnExitCodes = [0, 127, 137]
                            awaitStatusTimeout = 60000
                            execStopProbe(60000, 10000)
                        }
                        liveness {
                            livenessProbe(300000, 10000, 'database system is ready to accept connections')
                        }
                        exec {
                        
                            // can define as many of these commands as you'd like
                            // which will kick on initial **UP** only.
                            withCommand(['su', 'postgres', "-c", "/usr/local/bin/psql -c 'CREATE DATABASE devops'"])
                            withCommand(['sleep', '5'])
                            withCommand(['su', 'postgres', "-c", "/usr/local/bin/psql -c 'CREATE USER devops'"])
                            withCommand(['sleep', '5'])
                            withCommand(['su', 'postgres', "-c", "/usr/local/bin/psql -c 'GRANT ALL PRIVILEGES ON DATABASE devops TO devops'"])
                            
                            // sleeping as below calls can fail while the above is still catching up
                            withCommand(['sleep', '5'])
                            withCommand(['su', 'postgres', "-c", "/usr/local/bin/psql -c 'SELECT * FROM pg_settings'"])
                            withCommand(['su', 'postgres', "-c", "/usr/local/bin/pg_ctl status"])
                            withCommand(['printenv'])
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
            
            task stop(dependsOn: ['myPostgresStackStop']) {
                doLast {
                    println "REPORT SIZE: " + myPostgresStackStop.reports().size()
                    println "FOUND REPORT: " + myPostgresStackStop.reports().get(0)
                }
            }

            task down(dependsOn: ['myPostgresStackDown']) {
                doLast {
                    println "REPORT SIZE: " + myPostgresStackDown.reports().size()
                    println "FOUND REPORT: " + myPostgresStackDown.reports().get(0)
                }
            }
            
            task up(dependsOn: ['myPostgresStackUp']) {
                doLast {
                    println "REPORT SIZE: " + myPostgresStackUp.reports().size()
                    println "FOUND REPORT: " + myPostgresStackUp.reports().get(0)
                }
            }
                   
        """

        when:
        BuildResult resultUp = build( 'up')
        BuildResult resultStop = build( 'stop')
        BuildResult resultDown = build( 'down')

        then:
        resultUp.output.contains('In the KICKER')
        resultUp.output.contains('Inspecting container with ID')
        resultUp.output.contains('Created container with ID')
        count(resultUp.output, 'NETWORK = postgres-stack') == 4
        count(resultUp.output, 'Copying file to container') == 20
        resultUp.output.contains('Creating network')
        resultUp.output.contains('Copying file to container')
        resultUp.output.contains('Starting liveness')
        resultUp.output.contains('CI=TRUE')
        resultUp.output.contains('DEVOPS=ROCKS')
        resultUp.output.contains('CREATE DATABASE')
        resultUp.output.contains('CREATE ROLE')
        resultUp.output.contains('GRANT')
        resultUp.output.contains('pg_ctl: server is running')
        resultUp.output.contains('max_connections                        | 200')
        resultUp.output.contains('REPORT SIZE: 4')

        // we check for report size below to be 3 not because more are being
        // added, which they are not, but because we want to ensure it's only 3
        resultStop.output.contains('Running exec-stop on container with ID')
        resultStop.output.contains('REPORT SIZE: 4')

        resultDown.output.contains('Removing container with ID')
        resultDown.output.contains('Removing network')
        resultDown.output.contains('REPORT SIZE: 4')
    }
}
