package com.aries.gradle.docker.applications.plugin.worker

import com.aries.gradle.docker.applications.plugin.domain.CommandTypes
import com.aries.gradle.docker.applications.plugin.domain.DataContainer
import com.aries.gradle.docker.applications.plugin.domain.MainContainer
import org.gradle.api.Project

/**
 *
 * MetaData for the eventual worker thread to pick up and use for execution.
 *
 */
class WorkerMetaData {

    final CommandTypes command
    final Project project
    final String id
    final String index
    final String network
    final MainContainer mainContainer
    final DataContainer dataContainer
    final WorkerReport summaryReport

    final String mainId
    final String dataId

    WorkerMetaData(final CommandTypes command,
                   final Project project,
                   final String id,
                   final int index,
                   final String network,
                   final MainContainer mainContainer,
                   final DataContainer dataContainer,
                   final WorkerReport summaryReport) {

        this.command = command
        this.project = project
        this.id = id
        this.index = index
        this.network = network
        this.mainContainer = mainContainer
        this.dataContainer = dataContainer
        this.summaryReport = summaryReport

        this.mainId = "${id}-${index}"
        this.dataId = "${id}-data-${index}"
    }
}
