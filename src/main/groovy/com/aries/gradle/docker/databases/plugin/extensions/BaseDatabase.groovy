/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package com.aries.gradle.docker.databases.plugin.extensions

import org.gradle.api.tasks.Optional

/**
 *
 *
 *  Base class for all databases to inherit common functionality from.
 *
 */
public abstract class BaseDatabase {

    @Optional
    String id

    @Optional
    String repository // docker image id (e.g. postgres)

    @Optional
    String tag // docker tag id (e.g. latest or 10.0)

    @Optional
    String port // what port to expose database on. if set to an empty string a random port will be picked.

    @Optional
    Closure createDatabase // closure to further configure the `DockerCreateDatabase` task.

    @Optional
    Closure startDatabase // closure to further configure the `DockerStartDatabase` task.

    abstract String repository()

    abstract String defaultPort()

    String id() {
        this.id ?: System.getProperty("user.name")
    }

    String databaseId() {
        "${id()}-database"
    }

    String databaseDataId() {
        "${databaseId()}-data"
    }

    String tag() {
        this.tag ?: 'latest'
    }

    String image() {
        "${repository()}:${tag()}"
    }

    // helper method to set the `DockerCreateDatabase` closure
    void createDatabase(Closure closure) {
        this.createDatabase = closure
    }

    // helper method to set the `DockerStartDatabase` closure
    void startDatabase(Closure closure) {
        this.startDatabase = closure
    }
}
