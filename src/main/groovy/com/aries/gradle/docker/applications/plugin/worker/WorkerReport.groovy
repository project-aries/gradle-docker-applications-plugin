package com.aries.gradle.docker.applications.plugin.worker

import com.github.dockerjava.api.command.InspectContainerResponse

class WorkerReport {

    enum Status {
        SUBMITTED, // work is submitted and waiting to be run
        WAITING, // work has started but waiting on lock to proceed
        WORKING, // work is underway
        FINISHED // work has finished
    }

    InspectContainerResponse inspection

    String id
    String name
    String image
    String command
    String created
    final Map<String, String> ports = new HashMap<>()
    String address
    String gateway
    String network = 'bridge' // docker default network
    Status status = Status.SUBMITTED

    /**
     * Generate a banner to be displayed ... wherever!!!
     *
     * @return banner in string format.
     */
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

    /**
     * The `status` of this WorkerReport is updated as the execution of the
     * task at hand moves from submission to working to finished.
     *
     * @return current status in String format.
     */
    String status() {
        status.toString()
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
