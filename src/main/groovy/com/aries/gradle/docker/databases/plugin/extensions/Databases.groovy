/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package com.aries.gradle.docker.databases.plugin.extensions

/**
 *  Extension point that can be applied to all databases.
 */
class Databases extends BaseDatabase {

    String repository() {
        this.repository ?: null
    }
}

