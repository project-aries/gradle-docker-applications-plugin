package com.aries.gradle.docker.applications.plugin.domain

import org.codehaus.groovy.tools.shell.Command

enum CommandTypes {
    UP('Up'),
    STOP('Stop'),
    DOWN('Down')

    private final String type
    CommandTypes(final String type) {
        this.type = type
    }

    String type() { type }
}
