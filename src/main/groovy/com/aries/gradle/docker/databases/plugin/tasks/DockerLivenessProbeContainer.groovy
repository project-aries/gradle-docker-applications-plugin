package com.aries.gradle.docker.databases.plugin.tasks

import com.bmuschko.gradle.docker.tasks.container.DockerLogsContainer

import static com.aries.gradle.docker.databases.plugin.GradleDockerDatabasesPluginUtils.createProgressLogger

import org.gradle.internal.logging.progress.ProgressLoggerFactory
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.tasks.Input

/**
 *  Poll a given running container for an arbitrary log message to confirm liveness.
 */
class DockerLivenessProbeContainer extends DockerLogsContainer {

    @Input
    Probe probe

    @Override
    void runRemoteCommand(dockerClient) {
        logger.quiet "Starting liveness probe on container with ID '${getContainerId()}'."

        // create progressLogger for pretty printing of terminal log progression
        final def progressLogger = createProgressLogger(project, DockerLivenessProbeContainer)
        progressLogger.started()

        StringWriter writer = new StringWriter()
        boolean matchFound = false
        long localPollTime = probe.pollTime
        int pollTimes = 0

        while (localPollTime > 0) {

            // 1.) check if container is actually running
            def container = dockerClient.inspectContainerCmd(getContainerId()).exec()
            if (container.getState().getRunning() == false) {
                throw new GradleException("Container with ID '${getContainerId()}' is not running and so can't perform liveness probe.");
            }

            // 2.) check if next log line has the message we're interested in
            pollTimes = pollTimes + 1
            this.sink = writer
            specialRunRemoteCommand(dockerClient)
            String logLine = writer.toString()
            println "====================> FOUND LOG: ${logLine}"
            if (logLine && logLine.contains(probe.message)) {
                matchFound = true
                break
            } else {
                progressLogger.progress(sprintf('waiting for %010dms', pollTimes *probe.pollInterval ))
                try {

                    // zero'ing out the below so as to save on memory for potentially
                    // big logs returned from container.
                    logLine = null
                    writer.getBuffer().setLength(0)

                    localPollTime = localPollTime - probe.pollInterval
                    sleep(probe.pollInterval)
                } catch (Exception e) {
                    throw e
                }
            }
        }
        progressLogger.completed()

        if (!matchFound) {
            throw new GradleException("Liveness probe failed to find a match: ${probe.toString()}")
        }
    }

    // overridden version of `DockerLogsContainer` method `runRemoteCommand` to get the
    // same functionality but without the `logger.quiet` call that gets run every time.
    private specialRunRemoteCommand(dockerClient) {
        def logCommand = dockerClient.logContainerCmd(getContainerId())
        super.setContainerCommandConfig(logCommand)
        logCommand.exec(super.createCallback())?.awaitCompletion()
    }


    void probe(final long pollTime, final long pollInterval, final String message) {
        this.probe = new Probe(pollTime, pollInterval, message)
    }

    static class Probe {

        @Input
        long pollTime // how long we poll until match is found

        @Input
        long pollInterval // how long we wait until next poll

        @Input
        String message // halt polling on logs containing this String

        Probe(long pollTime, long pollInterval, String message) {
            if (pollInterval > pollTime) {
                throw new GradleException("pollInterval must be greater than pollTime: pollInterval=${pollInterval}, pollTime=${pollTime}")
            }

            String localMessage = message.trim()
            if (localMessage) {
                this.pollTime = pollTime
                this.pollInterval = pollInterval
                this.message = localMessage
            } else {
                throw new GradleException("message must be a valid non-empty String")
            }
        }

        @Override
        String toString() {
            "pollTime=${pollTime}, pollInterval=${pollInterval}, message='${message}'"
        }
    }
}
