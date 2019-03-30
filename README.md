# gradle-docker-applications-plugin

Highly opinionated gradle plugin to start (Up), pause (Stop), and delete (Down) an arbitrary docker application.

## Status

| CI | Codecov | Docs | Questions | Release |
| :---: | :---: | :---: | :---: | :---: |
| [![Build Status](https://travis-ci.org/project-aries/gradle-docker-applications-plugin.svg?branch=master)](https://travis-ci.org/project-aries/gradle-docker-applications-plugin) | [![codecov](https://codecov.io/gh/project-aries/gradle-docker-applications-plugin/branch/master/graph/badge.svg)](https://codecov.io/gh/project-aries/gradle-docker-applications-plugin) | [![Docs](https://img.shields.io/badge/docs-latest-blue.svg)](http://htmlpreview.github.io/?https://github.com/project-aries/gradle-docker-applications-plugin/blob/gh-pages/docs/index.html) | [![Stack Overflow](https://img.shields.io/badge/stack-overflow-4183C4.svg)](https://stackoverflow.com/questions/tagged/gradle-docker-applications-plugin) | [![gradle-docker-applications-plugin](https://api.bintray.com/packages/project-aries/libs-release-local/gradle-docker-applications-plugin/images/download.svg) ](https://bintray.com/project-aries/libs-release-local/gradle-docker-applications-plugin/_latestVersion) |

## Motivation, Design Goals, and who is this plugin for?

As maintainer of the [gradle-docker-plugin](https://github.com/bmuschko/gradle-docker-plugin) I often get questions about how best to use said plugin for standing up a given companies dockerized application in a gradle context. As the aforementioned plugin is compromised of many tasks, each acting as a low-level building block, it can be daunting for new users to understand how all of these tasks are wired together. That's where the `gradle-docker-applications-plugin` comes in. Being built on top of the [gradle-docker-plugin](https://github.com/bmuschko/gradle-docker-plugin) it provides an easy and intuitive way to define and configure your applications and then creates exactly **3** high-level tasks for you to manage them (more on that below).

When designing this plugin I wanted to get the following right without any compromises:

* Provide exactly **3** high-level tasks with which to manage a dockerized-application: **Up**, **Stop**, and **Down**.
* Allow for defining up to N number of dockerized-application(s) for extremely complex workloads.
* Be able to work in a complex multi-project setup with synchronization around all high-level tasks.
* Fail **ONLY** when necessary (i.e. invoking `Down` on an application that does not exist does not fail).
* Highly opinionated design for dockerized-application(s) based off of best practices.

Furthermore, this plugin solves the following problems for developers, devops, and site-reliability engineers:

* No longer need to maintain site specific images for minor changes (i.e. new tomcat image with added war file).
* Ability to stand-up a single dockerized-application(s) for integration testing which in turn can be shared and managed across multiple project(s).
* Well defined DSL to easily script the defintion of your dockerized-application.
* Removal of having to understand the inner workings of docker, or the `gradle-docker-plugin`, providing a fast way to get your dockerized-application defined and up and running with minimal effort.

## Getting Started

```
buildscript() {
    repositories {
        jcenter()
    }
    dependencies {
        classpath group: 'com.aries', name: 'gradle-docker-applications-plugin', version: 'X.Y.Z'
    }
}

apply plugin: 'gradle-docker-applications-plugin'
```
## Backend powered by _gradle-docker-plugin_

Because we use the [gradle-docker-plugin](https://github.com/bmuschko/gradle-docker-plugin) to drive this one: you are free to use the `docker` extension point to configure your docker connection as is described in more detail [HERE](https://bmuschko.github.io/gradle-docker-plugin/#extension).

## On the _applications_ extension point and DSL

This plugin is built around the idea that users will define N number of dockerized-application(s)
and we will hand back **3** appropriately named high-level tasks for you to manage said application. 
With this in mind we provide the `applications` extension point, which in turn is a [gradle container](http://mrhaki.blogspot.com/2016/02/gradle-goodness-using-nested-domain.html),
for you to define as many apps as you need:

```
applications {
    myPostgresStackOne {
    
    }
    myPostgresStackTwo {
    
    }
    myPostgresStackThree {
    
    }
}
```
Each app in turn is what we would call a dockerized-application and is just an instance of [AbstractApplication](https://github.com/project-aries/gradle-docker-applications-plugin/blob/master/src/main/groovy/com/aries/gradle/docker/applications/plugin/domain/AbstractApplication.groovy). These applications contain various properties and methods you can set and override as you see fit.

Furthermore each application allows you to define a `main` and optionally a `data` container like so:

```
applications {
   myPostgresStack {
      main { // required
      
      }
      data { // optional and will inherit image properties from `main` if not defined
      
      }
   }
}
```
Each dockerized-application gets exactly 2 containers created: **main** and **data**. The **main** container is your runtime or the thing that's actually running the application. The **data** container is the place the **main** container will write its data (or state) too thereby having a clear separation between the running instance and the data it creates.

The **main** container is an instance of [MainContainer](https://github.com/project-aries/gradle-docker-applications-plugin/blob/master/src/main/groovy/com/aries/gradle/docker/applications/plugin/domain/MainContainer.groovy) with the **data** container being an instance of [DataContainer](https://github.com/project-aries/gradle-docker-applications-plugin/blob/master/src/main/groovy/com/aries/gradle/docker/applications/plugin/domain/DataContainer.groovy) and both inherit from [AbstractContainer](https://github.com/project-aries/gradle-docker-applications-plugin/blob/master/src/main/groovy/com/aries/gradle/docker/applications/plugin/domain/AbstractContainer.groovy). In the end each are just mapped to docker containers with the caveat that the **data** container only ever gets created while the **main** container is not only created but is started and expected to stay running.

#### Requirements and Options

Both the **main** and **data** containers have a handful of closures/options for you to configure your dockerized application with. The only _real_ requirement is a defined **main** container with at least the image _repository_ set
like so:
```
applications {
    myPostgresStack {
        main {
            repository = 'postgres'
            tag = 'alpine' // optional and defaults to 'latest' if not set
        }
    }
}
```
So long as the image you're using starts an application, either through an `entrypoint` or `command`, and things stay in a running state, you're good to go.

The **data** container is also entirely optional and if not defined we will inherit the image _options_ (e.g. repository and tag) from the **main** container but NOTHING else.

#### Options available for both _main_ and _data_ container(s)

##### _create_ (Optional)

The **create** closure maps to [DockerCreateContainer](https://github.com/bmuschko/gradle-docker-plugin/blob/master/src/main/groovy/com/bmuschko/gradle/docker/tasks/container/DockerCreateContainer.groovy) and can optionally be defined **N** number of times. This allows you to configure the
creation of either container should the need arise:
```
applications {
    myPostgresStack {
        main {
            create {
                envVars = ['MAIN_CONTAINER' : 'true']
            }
            create {
                shmSize = 123456789
            }
        }
        data {
            create {
                withEnvVar('DATA_CONTAINER', 'true')
                withEnvVar('DEVOPS', 'ROCKS')
            }
        }
    }
}
```

##### _files_ (Optional)

The **files** closure maps to [DockerCopyFileToContainer](https://github.com/bmuschko/gradle-docker-plugin/blob/master/src/main/groovy/com/bmuschko/gradle/docker/tasks/container/DockerCopyFileToContainer.groovy) and can optionally be defined **N** number of times. This allows you to add an
arbitrary number of files to either container just after they've been created but just before they've been started:
```
// splitting things up into multiple files only to show that the user
// can add as many files as they want and can do so either outside
// of the _files_ closure as we're doing here or inside as is shown
// further below.
final File createDatabase = new File("$buildDir/CREATE_DATABASE.sql")
createDatabase.withWriter('UTF-8') {
    it.println('CREATE DATABASE docker;')
    createDatabase
}

final File grantPrivileges = new File("$buildDir/CREATE_DATABASE_USER_PRIVS.sql")
grantPrivileges.withWriter('UTF-8') {
    it.println('GRANT ALL PRIVILEGES ON DATABASE docker TO docker;')
    grantPrivileges
}

final File createCookie = new File("$buildDir/cookie.txt")
createCookie.withWriter('UTF-8') {
    it.println('Somebody Was Here')
    createCookie
}

applications {
    myPostgresStack {
        main {
            files {

                // demo adding files with string paths
                withFile("${createDatabase.path}", '/docker-entrypoint-initdb.d')

                // demo adding files with closures
                withFile({
                    final File createUser = new File("$buildDir/CREATE_DATABASE_USER.sql")
                    createUser.withWriter('UTF-8') {
                        it.println('CREATE USER docker;')
                        createUser
                    }
                }, { '/docker-entrypoint-initdb.d' })
            }
            files {

                // demo adding files with file objects
                withFile(project.file("${grantPrivileges.path}"), '/docker-entrypoint-initdb.d')
            }
        }
        data {
            files {
                withFile(project.file("$buildDir/cookie.txt"), '/') // demo with files
            }
        }
    }
}
```


#### Options available for the _main_ container

##### _liveness_ (Optional)

The **liveness** closure maps to [DockerLivenessContainer](https://github.com/bmuschko/gradle-docker-plugin/blob/master/src/main/groovy/com/bmuschko/gradle/docker/tasks/container/extras/DockerLivenessContainer.groovy) and can optionally be defined **N** number of times. This allows you to configure
the liveness probe, which runs just after the container is started, and probes (or polls) the running container for an arbitrary amount
of time waiting for the passed in String to become present in the container logs:
```
applications {
    myPostgresStack {
        main {
            liveness {

                // wait at most for 300000 milliseconds, probing every 10000 milliseconds, for
                // the log String noted below to be present.
                livenessProbe(300000, 10000, 'database system is ready to accept connections')
            }
        }
    }
}
```

##### _exec_ (Optional)

The **exec** closure maps to [DockerExecContainer](https://github.com/bmuschko/gradle-docker-plugin/blob/master/src/main/groovy/com/bmuschko/gradle/docker/tasks/container/DockerExecContainer.groovy) and can optionally be defined **N** number of times. This allows you to execute an arbitrary
number of commands on the **main** container AFTER we have deemed it to be live or in a running state:
```
applications {
    myPostgresStack {
        main {
            exec {
                withCommand(['su', 'postgres', "-c", "/usr/local/bin/psql -c 'CREATE DATABASE devops'"])
                withCommand(['su', 'postgres', "-c", "/usr/local/bin/psql -c 'CREATE USER devops'"])
                successOnExitCodes = [0] // if not defined will ignore all exit codes
            }
            exec {
                withCommand(['su', 'postgres', "-c", "/usr/local/bin/psql -c 'GRANT ALL PRIVILEGES ON DATABASE devops TO devops'"])
                successOnExitCodes = [0]
            }
        }
    }
}
```


##### _stop_ (Optional)

The **stop** closure maps to [DockerExecStopContainer](https://github.com/bmuschko/gradle-docker-plugin/blob/master/src/main/groovy/com/bmuschko/gradle/docker/tasks/container/extras/DockerExecStopContainer.groovy) and can optionally be defined **N** number of times.
This allows you to configure an optional **exec command** to be run within the **main** container to bring it down gracefully. If not defined
we revert back to just issueing a "stop" on the **main** container:
```
applications {
    myPostgresStack {
        main {
            stop {
                withCommand(['su', 'postgres', "-c", "/usr/local/bin/pg_ctl stop -m fast"])
                successOnExitCodes = [0, 127, 137]
                execStopProbe(60000, 10000) // time we wait for command(s) to finish

                // if above not defined this is the amount of time we will wait for container
                // to stop after issuing a "stop" request.
                timeout = 60000
            }
        }
    }
}
```

#### Complete example

```
applications {
    myPostgresStack {
        main {
            repository = 'postgres'
            tag = 'alpine'
            create {
                envVars = ['MAIN_CONTAINER' : 'true']
            }
            files {

                // create a new database, user, and privs with SQL file inside the main/runtime
                // container which will be invoked on initial start of the container only.
                // this is a postgres specific thing and is shown here only to document how one
                // might add/create/script a file dynamically.
                withFile({
                    final File createUser = new File("$buildDir/CREATE_DATABASE_AND_USER_WITH_PRIVS.sql")
                    createUser.withWriter('UTF-8') {
                        it.println('CREATE DATABASE docker;')
                        it.println('CREATE USER docker;')
                        it.println('GRANT ALL PRIVILEGES ON DATABASE docker TO docker;')
                        createUser
                    }
                }, { '/docker-entrypoint-initdb.d' })
            }
            exec {

                // create a new database, user, and privs AFTER container has started but BEFORE
                // the **UP** task-chain has finished using commands inside the container.
                withCommand(['su', 'postgres', "-c", "/usr/local/bin/psql -c 'CREATE DATABASE devops'"])
                withCommand(['su', 'postgres', "-c", "/usr/local/bin/psql -c 'CREATE USER devops'"])
                withCommand(['su', 'postgres', "-c", "/usr/local/bin/psql -c 'GRANT ALL PRIVILEGES ON DATABASE devops TO devops'"])
                successOnExitCodes = [0]
            }
            liveness {
                livenessProbe(300000, 10000, 'database system is ready to accept connections')
            }
            stop {
                withCommand(['su', 'postgres', "-c", "/usr/local/bin/pg_ctl stop -m fast"])
                successOnExitCodes = [0, 127, 137]
                timeout = 60000
                execStopProbe(60000, 10000)
            }
        }
        data {
            create {
                envVars = ['DATA_CONTAINER' : 'true']
            }
        }
    }
}
```

## On _Up_, _Stop_, and _Down_ tasks

Once an application is defined we create these **3** high-level tasks for the
end-user to work with in whatever way they see fit.

#### Up

Using the example from above: if your application is named `myPostgresStack` we will
create a task appropriately named `myPostgresStackUp` to start your dockerized
application. This task, after run the first time and the dockerized application started,
can be run repeatedly but more/less amounts to a no-op. We recognize that the instance
is already started and simply print out a banner saying things are live and allow you
to continue with the rest of your automation.

The **Up** task is the main workhorse of this project and as such has a bit more 
responsibility in providing the user with the final state of the _task-chain_. If 
for example you started a dockerized tomcat application that you created you would 
see output similar to the following:
```
> Task :tomcatUp
=====================================================================
ID = d0a676a06b666ed28132ad1e8120f850ad4011fd472f189c3525c0fd87ee117f
NAME = myTomcatServer-tomcat
IMAGE = tomcat:8.5-alpine
COMMAND = run
CREATED = 2018-06-30T12:05:48.222160433Z
ADDRESS = 172.17.0.1
PORTS = 32807->8080
LINKS = [:]
=====================================================================
```
Once the **Up** task has completed this banner is displayed giving you some basic 
information about the running dockerized application. Each of these properties, as 
well as an [InspectContainerResponse object](https://github.com/docker-java/docker-java/blob/master/src/main/java/com/github/dockerjava/api/command/InspectContainerResponse.java)
of the running container, are available to you as _gradle extension properties_ on the **Up** 
task itself but are ONLY set once the task has completed. An example of how you might 
access these could look like:
```
task myDownstreamTask(dependsOn: tomcatUp) {
    doLast {
        println tomcatUp.ext.id // String
        println tomcatUp.ext.name // String
        println tomcatUp.ext.image // String
        println tomcatUp.ext.command // List<String>
        println tomcatUp.ext.created // String
        println tomcatUp.ext.ports // Map<String, String>
        println tomcatUp.ext.links // List<String>
        
        // the actual inspection object itself which contains all of the
        // above as well as every other property/object attached to an
        // inspection you can think of.
        println tomcatUp.ext.inspection
    }
}
```

#### Stop

Using the example from above: if your application is named `myPostgresStack` we will
create a task appropriately named `myPostgresStackStop` to pause your dockerized
application. This task, after run the first time and the dockerized application stopped,
can be run repeatedly but more/less amounts to a no-op. We recognize that the instance
is already paused, print out a message saying as much, and allow you to continue with
 the rest of your automation.

#### Down

Using the example from above: if your application is named `myPostgresStack` we will
create a task appropriately named `myPostgresStackDown` to delete your dockerized
application. This task, after run the first time and the dockerized application deleted,
can be run repeatedly but more/less amounts to a no-op. We recognize that the instance
is already deleted, print out a message saying as much, and allow you to continue
with the rest of your automation.

## Tying it all together and basic usage

Now that you've defined your application you can can execute the generated tasks from the
command line like so:

```
// kick tasks from gradle command line
cdancy@gandalf:~$ ./gradle myPostgresStackUp myPostgresStackStop myPostgresStackDown
```

Or define more appropriately named tasks for your users to use:
```
task up(dependsOn: ['myPostgresStackUp'])

task stop(dependsOn: ['myPostgresStackStop'])

task down(dependsOn: ['myPostgresStackDown'])

check.dependsOn up
build.dependsOn stop
test.finalizedBy down
```


#### On container naming

The created container names themselves are built from a concatenation of the name of the application and the last part
of the repository (anything past last `/` or the whole repository name if none found). In turn you can expect 2
containers to be be made and named:

* **myPostgresStack-postgres** // started and expected to be in a running state
* **myPostgresStack-postgres-data** // never started and expected to be in a created state

## On Task Chain Synchronization

Each of our high-level tasks are considered a task-chain. The execution of these
task-chains are synchronized amongst the container names as is detailed further
above.

Within a given single/multi gradle project **ONLY ONE** invocation of our 3
high-level task-chains will be executed so as to ensure we don't clobber the docker
workload (i.e. task trying to delete a container while another is creating it).
For example: if you have 10 sub-projects and all attempt to start your dockerized
application **ONLY ONE** will be allowed to do so putting the other 9 in a
waiting state. Once the task-chain has finished the next task-chain waiting
for the lock will kick. While any task-chain is in a waiting state it will output
a gradle _progress logger_ to let the user know how long its been waiting.

