package com.aries.gradle.docker.applications.plugin.domain

import com.github.dockerjava.api.command.InspectContainerResponse

class SummaryReport {

    InspectContainerResponse inspection

    String id
    String name
    String image
    String command
    String created
    final Map<String, String> ports = new HashMap<>()
    String address
    String gateway
    String network = 'bridge' // docker default

    String banner() {

        final StringBuilder builder = new StringBuilder()

        String banner = '====='
        id.length().times { banner += '=' }

        final String portsAsString = ports ? ports.collect { k, v -> "${v}->${k}" }.join(',') : ports.toString()

        final String sep = System.lineSeparator()
        builder.append(banner).append(sep)
            .append("ID = ${id}").append(sep)
            .append("NAME = ${name}").append(sep)
            .append("IMAGE = ${image}").append(sep)
            .append("COMMAND = ${command}").append(sep)
            .append("CREATED = ${created}").append(sep)
            .append("PORTS = ${portsAsString}").append(sep)
            .append("ADDRESS = ${address}").append(sep)
            .append("GATEWAY = ${gateway}").append(sep)
            .append("NETWORK = ${network}").append(sep)
            .append(banner)

        return builder.toString()
    }

    @Override
    String toString() {
        final String portsAsString = ports ? ports.collect { k, v -> "${v}->${k}" }.join(',') : ports.toString()
        return "{'id' : '$id', " +
            "'name' : '$name', " +
            "'image' : '$image', " +
            "'command' : '$command', " +
            "'created' : '$created', " +
            "'ports' : '$portsAsString', " +
            "'address' : '$address', " +
            "'gateway' : '$gateway', " +
            "'network' : '$network' }"
    }
}
