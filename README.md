# gradle-docker-application-plugin

Highly opinionated gradle plugin to start (Up), pause (Stop), and delete (Down) an arbitrary docker application.

## Status

| CI | Codecov | Docs | Questions | Release |
| :---: | :---: | :---: | :---: | :---: |
| [![Build Status](https://travis-ci.org/project-aries/gradle-docker-application-plugin.svg?branch=master)](https://travis-ci.org/project-aries/gradle-docker-application-plugin) | [![codecov](https://codecov.io/gh/project-aries/gradle-docker-application-plugin/branch/master/graph/badge.svg)](https://codecov.io/gh/project-aries/gradle-docker-application-plugin) | [![Docs](https://img.shields.io/badge/docs-latest-blue.svg)](http://htmlpreview.github.io/?https://github.com/project-aries/gradle-docker-application-plugin/blob/gh-pages/docs/index.html) | [![Stack Overflow](https://img.shields.io/badge/stack-overflow-4183C4.svg)](https://stackoverflow.com/questions/tagged/gradle-docker-application-plugin) | [![gradle-docker-application-plugin](https://api.bintray.com/packages/project-aries/libs-release-local/gradle-docker-application-plugin/images/download.svg) ](https://bintray.com/project-aries/libs-release-local/gradle-docker-application-plugin/_latestVersion) |


## Motivation and Design Goals

As the maintainer of the [gradle-docker-plugin](https://github.com/bmuschko/gradle-docker-plugin) I often get questions about how best to use said plugin for standing up a given companies dockerized application in a gradle context. As the aforementioned plugin is compromised of many tasks, each acting as a low-level build block, it can be daunting for new users to understand how all of these tasks are wired together. That's where the `gradle-docker-application-plugin` comes in. It's built on top of the [gradle-docker-plugin](https://github.com/bmuschko/gradle-docker-plugin) and provides an easy and intuitive way to define your application and then creates a handful of tasks for you to manage it.

When designing this plugin we had some things we wanted to get right without any compromises:

* Provide exactly **3** high-level tasks with which to manage the dockerized application: **Up**, **Stop**, and **Down**.
* Allow for defining up to N number of dockerized-applications for extremely complex workloads.
* Be able to work in a complex multi-project setup with synchronization around all high-level tasks.
* Fail **ONLY** when necessary (i.e. invoking `Down` on an application that does not exist does not fail).
* Highly opinionated design for dockerized applications based off of best practices and years of experience.


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

## On the _applications_ extension point

This plugin is built around the idea that users will define N number of dockerized application(s) and we will hand back 3 appropriately named high-level tasks for you to manage said application with.

## On _Up_, _Stop_, and _Down_ tasks

Once an application is defined we create 3 high-level tasks for the end-user to work with:

#### Up

Details to follow

#### Stop

Details to follow

#### Down

Details to follow

