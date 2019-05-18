package com.aries.gradle.docker.applications.plugin.domain

class GradleLock {
    String name = GradleLock.class.simpleName
    boolean lock = false
    boolean unlock = false
}
