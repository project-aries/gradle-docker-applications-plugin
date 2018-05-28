/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package com.aries.gradle.docker.databases.plugin.extensions

import com.aries.gradle.docker.databases.plugin.common.ExtensionHelpers

/**
 *  Sqlserver specific extension point.
 */
class Sqlserver extends BaseDatabase implements ExtensionHelpers {

    String repository() {
        this.repository ?: 'microsoft/mssql-server-linux'
    }

    String defaultPort() {
        "1433"
    }

    String liveOnLog() {
        this.liveOnLog ?: 'database system is ready to accept connections'
    }
}

