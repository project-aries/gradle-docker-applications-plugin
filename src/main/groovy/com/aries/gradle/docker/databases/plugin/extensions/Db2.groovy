/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package com.aries.gradle.docker.databases.plugin.extensions

import com.aries.gradle.docker.databases.plugin.common.ExtensionHelpers

/**
 *  Db2 specific extension point.
 */
class Db2 extends BaseDatabase implements ExtensionHelpers {

    String repository() {
        this.repository ?: 'ibmcom/db2express-c'
    }

    String defaultPort() {
        "50000"
    }
}

