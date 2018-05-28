/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package com.aries.gradle.docker.databases.plugin.extensions

import com.aries.gradle.docker.databases.plugin.common.ExtensionHelpers

/**
 *  Oracle specific extension point.
 */
class Oracle extends BaseDatabase implements ExtensionHelpers {

    String repository() {
        this.repository ?: 'oracle/database'
    }

    String defaultPort() {
        "1521"
    }

    String liveOnLog() {
        this.liveOnLog ?: 'database system is ready to accept connections'
    }
}

