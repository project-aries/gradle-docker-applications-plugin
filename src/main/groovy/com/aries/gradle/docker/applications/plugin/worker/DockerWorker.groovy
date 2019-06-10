package com.aries.gradle.docker.applications.plugin.worker

import com.aries.gradle.docker.applications.plugin.domain.CommandTypes
import com.aries.gradle.docker.applications.plugin.domain.DataContainer
import com.aries.gradle.docker.applications.plugin.domain.MainContainer
import com.aries.gradle.docker.applications.plugin.report.SummaryReport
import com.bmuschko.gradle.docker.tasks.DockerOperation
import com.bmuschko.gradle.docker.tasks.container.*
import com.bmuschko.gradle.docker.tasks.container.extras.DockerExecStopContainer
import com.bmuschko.gradle.docker.tasks.container.extras.DockerLivenessContainer
import com.bmuschko.gradle.docker.tasks.image.DockerInspectImage
import com.bmuschko.gradle.docker.tasks.image.DockerPullImage
import com.bmuschko.gradle.docker.tasks.network.DockerCreateNetwork
import com.bmuschko.gradle.docker.tasks.network.DockerRemoveNetwork
import com.github.dockerjava.api.model.ContainerNetwork
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task

import javax.inject.Inject

import static com.aries.gradle.docker.applications.plugin.GradleDockerApplicationsPluginUtils.*

/**
 *
 * Invokes the requested command (e.g. UP, STOP, DOWN) on a single dockerized application.
 *
 */
class DockerWorker implements Runnable {

    final WorkerMetaData workerMetaData

    @Inject
    DockerWorker(final String cacheKey) {
        this.workerMetaData = WorkerMetaDataCache.get(cacheKey)
    }

    @Override
    void run() {

        final String lockName = "execution-${workerMetaData.getMainId()}-lock"

        try {

            // 1.) update status to denote we are now waiting for lock
            workerMetaData.summaryReport.status = SummaryReport.Status.WAITING

            // 2.) wait for lock to proceed on this work
            acquireLock(workerMetaData.project, lockName)

            // 3.) update status to denote we are now working
            workerMetaData.summaryReport.status = SummaryReport.Status.WORKING

            // 4.) perform requested work
            switch (workerMetaData.command) {
                case CommandTypes.UP:
                    up(true);
                    up(false);
                    break;
                case CommandTypes.STOP:
                    stop();
                    break
                case CommandTypes.DOWN:
                    down(false);
                    down(true);
                    break
            }
        } finally {

            // 5.) release lock for this work
            releaseLock(workerMetaData.project, lockName)

            // 6.) update status to denote we are now waiting for lock
            workerMetaData.summaryReport.status = SummaryReport.Status.FINISHED
        }
    }

    private void up(final boolean isDataContainer) {

        final Project project = workerMetaData.project

        final String mainId = workerMetaData.getMainId()
        final String dataId = workerMetaData.getDataId()

        final MainContainer mainContainer = workerMetaData.mainContainer
        final DataContainer dataContainer = workerMetaData.dataContainer

        final String containerId = isDataContainer ? dataId : mainId
        final String repositoryId = isDataContainer ? dataContainer.repository() : mainContainer.repository()
        final String tagId = isDataContainer ? dataContainer.tag() : mainContainer.tag()
        final String imageId = isDataContainer ? dataContainer.image() : mainContainer.image()
        final String networkName = workerMetaData.network

        final List createContainerConfigs = isDataContainer ? dataContainer.createConfigs : mainContainer.createConfigs
        final List copyFileConfigs = isDataContainer ? dataContainer.filesConfigs : mainContainer.filesConfigs
        final List livenessConfigs = isDataContainer ? null : mainContainer.livenessConfigs
        final List execConfigs = isDataContainer ? null : mainContainer.execConfigs

        final List<String> volumesFromContainers = isDataContainer ? [] : ["${dataId}"]

        // 1.) Check if container is currently available
        Task availableContainerTask = createTask(project, DockerInspectContainer, { cnf ->
            cnf.targetContainerId(containerId)
            cnf.ext.exists = false
            cnf.ext.inspection = null
            cnf.ext.isRunning = false
            cnf.ext.hasNetwork = false
            cnf.onNext { possibleContainer ->
                cnf.ext.exists = true
                cnf.ext.inspection = possibleContainer
                cnf.ext.isRunning = possibleContainer.state.running
                cnf.ext.hasNetwork = possibleContainer.getNetworkSettings().getNetworks().containsKey(networkName)
            }
            cnf.onError { err ->
                throwOnValidError(err)
            }
        })
        executeTaskCode(availableContainerTask)

        // 2.) Create network if requested and not present
        if (networkName && !availableContainerTask.ext.hasNetwork) {
            Task createNetworkTask = createTask(project, DockerCreateNetwork, { cnf ->
                cnf.onlyIf {
                    try {
                        cnf.dockerClient.inspectNetworkCmd().withNetworkId(networkName).exec()
                        false
                    } catch (Exception e) {
                        true
                    }
                }

                cnf.networkId = networkName
            })

            final String lockName = "network-${networkName}-lock"
            executeTaskCode(createNetworkTask, lockName)
        }

        boolean restartedContainer = false
        Date restartDate = null
        if (availableContainerTask.ext.exists == true) {

            // 3.) Restart container but only if it's available and not in a running state
            if (isDataContainer == false && availableContainerTask.ext.inspection.state.running == false) {
                Task restartContainerTask = createTask(project, DockerRestartContainer, { cnf ->
                    restartDate = new Date()

                    cnf.targetContainerId(containerId)
                    cnf.waitTime = 3000
                })
                executeTaskCode(restartContainerTask)
                restartedContainer = true
            }

        } else {

            // 4.) Pull image for container if it does not already exist
            Task inspectImageTask = createTask(project, DockerInspectImage, { cnf ->
                cnf.targetImageId(imageId)
                cnf.ext.hasImage = false
                cnf.onNext {
                    cnf.ext.hasImage = true
                }
                cnf.onError { err ->
                    throwOnValidError(err)
                }
            })
            executeTaskCode(inspectImageTask)

            if (inspectImageTask.ext.hasImage == false) {
                Task pullImageTask = createTask(project, DockerPullImage, { cnf ->
                    cnf.repository = repositoryId
                    cnf.tag = tagId
                    cnf.onError { err ->
                        throwOnValidError(err)
                    }
                })
                executeTaskCode(pullImageTask)
            }

            // 5.) create, copy files to, and start container if it didn't previously exist
            Task createContainerTask = createTask(project, DockerCreateContainer, { cnf ->
                cnf.network = networkName
                cnf.targetImageId(imageId)
                cnf.containerName = containerId
                cnf.volumesFrom = volumesFromContainers
            })
            executeTaskCode(createContainerTask, createContainerConfigs)

            if (copyFileConfigs) {
                Task copyFilesToContainerTask = createTask(project, DockerCopyFileToContainer, { cnf ->
                    cnf.targetContainerId(containerId)
                })
                executeTaskCode(copyFilesToContainerTask, copyFileConfigs)
            }

            if (isDataContainer == false) {
                Task startContainerTask = createTask(project, DockerStartContainer, { cnf ->
                    cnf.ext.startTime = new Date()
                    cnf.targetContainerId(containerId)
                })
                executeTaskCode(startContainerTask)
            }
        }

        if (isDataContainer == false) {

            // pause done to allow the container to come up and potentially
            // exit (e.g. container that has no entrypoint or cmd defined).
            sleep(2000)

            // 6.) perform liveness check to confirm container is running
            Task livenessContainerTask = createTask(project, DockerLivenessContainer, { cnf ->

                cnf.targetContainerId(containerId)

                // only 2 ways this task can kick so we will proceed to configure
                // the `since` option based ONLY upon a "restart" scenario as we will
                // use it to determine where in the logs we should start from whereas
                // in the "start" scenario we simply start from the very beginning
                // of the logs docker gives us.
                cnf.since.set(restartDate ?: null)
                cnf.onComplete {

                    // though we should be live at this point we sleep for
                    // another 2 seconds to give the potential application
                    // some breathing room before we start hammering away
                    // on it with potential requests.
                    sleep(2000)
                }
            })
            executeTaskCode(livenessContainerTask, livenessConfigs)

            // 7.) Run any "exec" tasks inside the container now that it's started.
            //     This task is ONLY meant to kick on first startup therefor we skip
            //     execution if the container was already started or if we had to re-start it.
            boolean execStarted = false
            if (execConfigs && (availableContainerTask.ext.isRunning == false) && (restartedContainer == false)) {
                Task execContainerTask = createTask(project, DockerExecContainer, { cnf ->

                    cnf.targetContainerId(containerId)

                    cnf.onComplete {

                        // sleeping for 2 seconds just in-case any command caused this container to
                        // come down, potentially gracefully, before we presume things are live.
                        sleep(2000)
                    }
                })
                executeTaskCode(execContainerTask, execConfigs)
                execStarted = true
            }

            // 8.) get the summary for the running container and print to stdout
            final SummaryReport summaryReport = workerMetaData.summaryReport
            Task summaryContainerTask = createTask(project, DockerOperation, { cnf ->

                cnf.onNext { dockerClient ->

                    // 1.) Set the last used "inspection" for potential downstream use
                    if (execStarted) {

                        // if the `execContainerTask` task kicked we need to
                        // make an additional inspection call to ensure things
                        // are still live and running just to be on the safe side.
                        summaryReport.inspection = dockerClient.inspectContainerCmd(containerId).exec()
                        if (!summaryReport.inspection.state.running) {
                            throw new GradleException("Container '${containerId}' was NOT in a running state after exec(s) finished. Was this expected?")
                        }
                    } else {
                        summaryReport.inspection = livenessContainerTask.lastInspection()
                    }

                    // 2.) set handful of variables for easy access and downstream use
                    summaryReport.id = summaryReport.inspection.id
                    summaryReport.name = summaryReport.inspection.name.replaceFirst('/', '')
                    summaryReport.image = summaryReport.inspection.getConfig().image
                    summaryReport.command = (summaryReport.inspection.getConfig().getEntrypoint()) ? summaryReport.inspection.getConfig().getEntrypoint().join(' ') : null
                    if (summaryReport.inspection.getArgs()) {
                        if (!summaryReport.command) {
                            summaryReport.command = ""
                        }
                        summaryReport.command = ("${summaryReport.command} " + summaryReport.inspection.getArgs().join(' ')).trim()
                    }
                    summaryReport.created = summaryReport.inspection.created
                    if (summaryReport.inspection.getNetworkSettings().getPorts()) {
                        summaryReport.inspection.getNetworkSettings().getPorts().getBindings().each { k, v ->
                            def key = '' + k.getPort()
                            def value = '' + (v ? v[0].hostPortSpec : 0)
                            summaryReport.ports.put(key, value)
                        }
                    }

                    // find the proper network to use for downstream consumption
                    String foundNetwork = null
                    if (summaryReport.inspection.getNetworkSettings().getNetworks().isEmpty()) {
                        summaryReport.address = null
                        summaryReport.gateway = null
                    } else {
                        ContainerNetwork containerNetwork
                        if (networkName) {
                            containerNetwork = summaryReport.inspection.getNetworkSettings().getNetworks().get(networkName)
                            foundNetwork = networkName
                        } else {
                            for (final Map.Entry<String, ContainerNetwork> entry : summaryReport.inspection.getNetworkSettings().getNetworks().entrySet()) {
                                if (!entry.getKey().equals('none')) {
                                    foundNetwork = entry.getKey()
                                    containerNetwork = entry.getValue()
                                    break
                                }
                            }
                        }

                        summaryReport.address = containerNetwork?.getIpAddress()
                        summaryReport.gateway = containerNetwork?.getGateway()
                    }

                    summaryReport.network = foundNetwork

                    // 3.) print banner to stdout as an indication that we are now live
                    cnf.logger.quiet '' // newline just to put a break between last output and this banner being printed
                    cnf.logger.quiet summaryReport.banner()
                }
            })
            executeTaskCode(summaryContainerTask)
        }
    }

    private void stop() {

        final Project project = workerMetaData.project

        final String mainId = workerMetaData.getMainId()

        Task execStopContainerTask = createTask(project, DockerExecStopContainer, { cnf ->

            cnf.targetContainerId(mainId)

            cnf.onNext { output ->
                // pipe output to nowhere for the time being
            }
            cnf.onError { err ->
                throwOnValidError(err)
            }
        })
        executeTaskCode(execStopContainerTask, workerMetaData.mainContainer.stopConfigs)
    }

    private void down(final boolean isDataContainer) {

        final Project project = workerMetaData.project

        final String mainId = workerMetaData.getMainId()
        final String dataId = workerMetaData.getDataId()

        final String containerId = isDataContainer ? dataId : mainId
        final String networkName = workerMetaData.network

        Task deleteContainerTask = createTask(project, DockerRemoveContainer, { cnf ->

            cnf.removeVolumes = true
            cnf.force = true
            cnf.targetContainerId(containerId)

            cnf.onError { err ->
                throwOnValidError(err)
            }
        })
        executeTaskCode(deleteContainerTask)

        if (networkName) {
            Task removeNetworkTask = createTask(project, DockerRemoveNetwork, { cnf ->
                cnf.onlyIf {
                    try {
                        cnf.dockerClient.inspectNetworkCmd().withNetworkId(networkName).exec().containers.size() == 0
                    } catch (final Exception e) {
                        false
                    }
                }

                cnf.targetNetworkId(networkName)

                cnf.onError { err ->
                    throwOnValidError(err)
                }
            })
            final String lockName = "network-${networkName}-lock"
            executeTaskCode(removeNetworkTask, lockName)
        }
    }
}
