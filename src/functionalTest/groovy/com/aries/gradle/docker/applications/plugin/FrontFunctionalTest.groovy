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
 *  Functional tests for using the front-end container.
 *
 */
class FrontFunctionalTest extends AbstractFunctionalTest {

    @Timeout(value = 2, unit = MINUTES)
    def "Can start, stop, and remove a tomcat application stack with frontend"() {

        buildFile << """

            def sharedNetworkName = 'bridge'

            applications {
                
                myTomcatStack {
                    network(sharedNetworkName)
                    count(3)
                    main {
                        repository = 'tomcat'
                        tag = '9.0.21-jdk11-openjdk-slim'
                        of {
                            portBindings = [':8080']
                        }
                        liveness {
                            livenessProbe(300000, 5000, 'org.apache.catalina.startup.Catalina.start Server startup in')
                        }
                    }
                    front {
                        proxy {
                            expose(55555)
                            method('ip_hash')
                            matching('bear')
                        }
                        proxy {
                            expose(4444, 8080)
                            method('ip_hash')
                            matching('fish')
                        }
                    }
                }
            }
            
            task stop(dependsOn: ['myTomcatStackStop']) {
                doLast {
                    println "REPORT SIZE: " + myTomcatStackStop.reports().size()
                    println "FOUND REPORT: " + myTomcatStackStop.reports().get(0)
                }
            }

            task down(dependsOn: ['myTomcatStackDown']) {
                doLast {
                    println "REPORT SIZE: " + myTomcatStackDown.reports().size()
                    println "FOUND REPORT: " + myTomcatStackDown.reports().get(0)
                }
            }
            
            task up(dependsOn: ['myTomcatStackUp']) {
                doLast {
                    println "REPORT SIZE: " + myTomcatStackUp.reports().size()
                    println "FOUND REPORT: " + myTomcatStackUp.reports().get(0)
                }
            }
                   
        """

        when:
        BuildResult resultUp = build( 'up')
        BuildResult resultStop = build( 'tasks')
        BuildResult resultDown = build( 'tasks')

        then:
        resultUp.output.contains('REPORT SIZE: 3')
        resultUp.output.contains('-front')

        resultStop.output.contains('Running exec-stop on container with ID')
        resultStop.output.contains('REPORT SIZE: 3')

        resultDown.output.contains('Removing container with ID')
        resultDown.output.contains('Removing network')
        resultDown.output.contains('REPORT SIZE: 3')
    }
}
