# gradle-docker-application-plugin

Highly opinionated gradle plugin to start (Up), pause (Stop), and delete (Down) an arbitrary docker application.

## Status

| CI | Codecov | Docs | Questions | Release |
| :---: | :---: | :---: | :---: | :---: |
| [![Build Status](https://travis-ci.org/project-aries/gradle-docker-application-plugin.svg?branch=master)](https://travis-ci.org/project-aries/gradle-docker-application-plugin) | [![codecov](https://codecov.io/gh/project-aries/gradle-docker-application-plugin/branch/master/graph/badge.svg)](https://codecov.io/gh/project-aries/gradle-docker-application-plugin) | [![Docs](https://img.shields.io/badge/docs-latest-blue.svg)](http://htmlpreview.github.io/?https://github.com/project-aries/gradle-docker-application-plugin/blob/gh-pages/docs/index.html) | [![Stack Overflow](https://img.shields.io/badge/stack-overflow-4183C4.svg)](https://stackoverflow.com/questions/tagged/gradle-docker-application-plugin) | [![gradle-docker-application-plugin](https://api.bintray.com/packages/project-aries/libs-release-local/gradle-docker-application-plugin/images/download.svg) ](https://bintray.com/project-aries/libs-release-local/gradle-docker-application-plugin/_latestVersion) |


## Motivation and Design Goals

As maintainer of the [gradle-docker-plugin](https://github.com/bmuschko/gradle-docker-plugin) I often get questions about how best to use said plugin for standing up a given companies dockerized application in a gradle context. As the aforementioned plugin is compromised of many tasks, each acting as a low-level building block, it can be daunting for new users to understand how all of these tasks are wired together. That's where the `gradle-docker-application-plugin` comes in. Being built on top of the [gradle-docker-plugin](https://github.com/bmuschko/gradle-docker-plugin) it provides an easy and intuitive way to define and configure your applications and then creates exactly **3** high-level tasks for you to manage them (more on that below).

When designing this plugin I wanted to get the following right without any compromises:

* Provide exactly **3** high-level tasks with which to manage a dockerized-application: **Up**, **Stop**, and **Down**.
* Allow for defining up to N number of dockerized-application(s) for extremely complex workloads.
* Be able to work in a complex multi-project setup with synchronization around all high-level tasks.
* Fail **ONLY** when necessary (i.e. invoking `Down` on an application that does not exist does not fail).
* Highly opinionated design for dockerized-application(s) based off of best practices.


## Getting Started

```
buildscript() {
    repositories {
        jcenter()
    }
    dependencies {
        classpath group: 'com.aries', name: 'gradle-docker-application-plugin', version: 'X.Y.Z'
    }
}

apply plugin: 'gradle-docker-application-plugin'
```
## Built on top of `gradle-docker-plugin`

Because we use the [gradle-docker-plugin](https://github.com/bmuschko/gradle-docker-plugin) to drive this one: you are free to use the `docker` extension point to configure your docker connection as is described in more detail [HERE](https://github.com/bmuschko/gradle-docker-plugin#extension-examples).

## On the _applications_ extension point and DSL

This plugin is built around the idea that users will define N number of dockerized-application(s)
and we will hand back **3** appropriately named high-level tasks for you to manage said application. 
With this in mind we provide the `applications` extension point, which in turn is a [gradle container](http://mrhaki.blogspot.com/2016/02/gradle-goodness-using-nested-domain.html),
for you to define as many apps as you need:

```
applications {
    myAppOne {
    
    }
    myAppTwo {
    
    }
    myAppThree {
    
    }
}
```
Each app in turn is what we would call a dockerized-application and is just an instance of [AbstractApplication](https://github.com/project-aries/gradle-docker-application-plugin/blob/master/src/main/groovy/com/aries/gradle/docker/application/plugin/domain/AbstractApplication.groovy). These applications contain various properties and methods you can set and override as you see fit.

Furthermore each application allows you to define a `main` and optionally a `data` container like so:

```
applications {
   myAppOne {
      main { // required
      
      }
      data { // optional and will inherit image properties from `main` if not defined
      
      }
   }
}
```
Each dockerized-application gets exactly 2 containers created: **main** and **data**. The **main** container is your runtime or the thing that's actually running the application. The **data** container is the place the **main** container will write its data (or state) too thereby having a clear separation between the running instance and the data it creates.

The **main** container is an instance of [MainContainer](https://github.com/project-aries/gradle-docker-application-plugin/blob/master/src/main/groovy/com/aries/gradle/docker/application/plugin/domain/MainContainer.groovy) with the **data** container being an instance of [DataContainer](https://github.com/project-aries/gradle-docker-application-plugin/blob/master/src/main/groovy/com/aries/gradle/docker/application/plugin/domain/DataContainer.groovy) and both inherit from [AbstractContainer](https://github.com/project-aries/gradle-docker-application-plugin/blob/master/src/main/groovy/com/aries/gradle/docker/application/plugin/domain/AbstractContainer.groovy). Each are just docker containers at the end of the day with the caveat that the **data** container only ever gets created while the **main** container is not only created but is started and expected to stay running.

A real world example on how to stand-up a postgres alpine database would look like:

```
applications {
    myPostgresStack {
        id = "devops" // defaults to user.name if not defined
        main {
            repository = 'postgres'
            tag = 'alpine'
            create {
                env = ['CI=TRUE', 'DEVOPS=ROCKS']
            }
            stop {
                withCommand(['su', 'postgres', "-c", "/usr/local/bin/pg_ctl stop -m fast"])
                successOnExitCodes = [0, 127, 137]
                timeout = 60000
                probe(60000, 10000)
            }
            liveness {
                probe(300000, 10000, 'database system is ready to accept connections')
            }
            exec {
                withCommand(['echo', 'Hello World'])
                withCommand(['date'])
                withCommand(['su', 'postgres', "-c", "/usr/local/bin/pg_ctl status"])
                successOnExitCodes = [0]
            }
        }
    }
}
```

The `myPostgresStack` is the name of the application but we could have named it anything. It's relevant **ONLY** to this gradle project and serves as an easy way to namespace the generated task names we create behind the scenes (e.g. myPostgresStackUp, myPostgresStackStop, myPostgresStackDown).

A bit more on this example and what it does:

* Defines the **main** container which is documented below.
* Sets the repository and tag to use the _postgres alpine_ image.
* Configures the optional **_create_** task to further customize how we want the **main** container to be built.
* Configures the optional **_stop_** task to execute a command to gracefully bring the container down from within (defaults to stopping container).
* Configures the optional **_liveness_** task to probe the **main** container to wait at most _300000_ milliseconds, polling every _10000_ milliseconds, for the existence of the given String at which point the container will be considered live (defaults to checking if container is in a **running** state).
* Configures the optional **_exec_** task to execute an arbitrary number of command(s) within the container after liveness has been attained.

Each configuration can optionally be set **N** times for more complicated scenarios:
```
applications {
    multiConfigExample {
        main {
            create {
                env << ['ONE=FISH', 'TWO=FISH']
            }
            create {
                env << ['RED=FISH', 'BLUE=FISH']
            }
        }
    }
}
```

As noted above: the name of the application is used **ONLY** for task naming purposes and is meant to
provide an easy way for developers to code for these tasks. The container names themselves are built
from a concatenation of the `id` noted above and the last part of the repository (anything past last
`/` or the whole repository name if none found). In turn you can expect 2 containers to be be made and named:

* **devops-postgres** // started and expected to be in a running state
* **devops-postgres-data** // never started and expected to be in a created state

Once your dockerized-application is live you can do things like:
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

## On Task Chain Synchronization

Each of our high-level tasks are considered a task-chain. The execution of these
task-chains are synchronized amongst the container names as is detailed further
above.

Within a given single/multi gradle project **ONLY ONE** invocation of our 3
high-level task-chains will be executed so as to ensure we don't clobber the docker
workload (i.e. task trying to delete a container while another is creating it).
For example: if you have 10 sub-projects and all attempt to start your dockerized
application **ONLY ONE** will be allowed to do so putting the other 9 in a
waiting state. Once the system has become live another will attempt to do so,
recognize the system is already up, and proceed with the next task to execute
and so on and so forth.

