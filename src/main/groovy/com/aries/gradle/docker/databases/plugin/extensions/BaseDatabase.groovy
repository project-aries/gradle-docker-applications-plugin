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
    String repository

    @Optional
    String tag

    abstract String repository()
    
    String tag() {
        this.tag ?: 'latest'
    }

    String image() {
        "${repository()}:${tag()}"
    }
}

