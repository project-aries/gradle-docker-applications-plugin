package com.aries.gradle.docker.databases.plugin.common

trait ExtensionHelpers {

    List portMappings() {
        def ports = []
        if (this.port != null) {
            def localPort = this.port.trim()
            if (localPort.contains(':')) {
                ports << localPort
            } else {
                ports << "${localPort}:${defaultPort() ?: localPort}"
            }
        }
        ports
    }
}
