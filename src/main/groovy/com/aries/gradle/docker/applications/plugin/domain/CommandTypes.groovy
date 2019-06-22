package com.aries.gradle.docker.applications.plugin.domain

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
