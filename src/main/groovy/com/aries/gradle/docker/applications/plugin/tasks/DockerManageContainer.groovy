/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.aries.gradle.docker.applications.plugin.tasks

import com.aries.gradle.docker.applications.plugin.domain.*
import com.aries.gradle.docker.applications.plugin.worker.DockerWorker
import com.aries.gradle.docker.applications.plugin.report.SummaryReport
import com.aries.gradle.docker.applications.plugin.worker.WorkerMetaData
import com.aries.gradle.docker.applications.plugin.worker.WorkerMetaDataCache
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.util.ConfigureUtil
import org.gradle.workers.IsolationMode
import org.gradle.workers.WorkerExecutor

import javax.inject.Inject

import static java.util.Objects.requireNonNull

/**
 *
 * Manage the lifecycle of dockerized application-container(s)
 *
 */
class DockerManageContainer extends DefaultTask {

    @Input
    @Optional
    final Property<String> command = project.objects.property(String)

    @Input
    @Optional
    final Property<Integer> count = project.objects.property(Integer)

    @Input
    @Optional
    final Property<String> id = project.objects.property(String)

    @Input
    @Optional
    final Property<String> network = project.objects.property(String)

    @Internal
    private final List<Closure<MainContainer>> mainConfigs = []

    @Internal
    private final List<Closure<DataContainer>> dataConfigs = []

    @Internal
    private final List<SummaryReport> reports = []

    @Internal
    private final WorkerExecutor workerExecutor

    @Inject
    DockerManageContainer(final WorkerExecutor workerExecutor) {
        this.workerExecutor = workerExecutor
    }

    @TaskAction
    void execute() {

        // 1.) Initialize all properties and set defaults where necessary
        final CommandTypes resolvedCommand = CommandTypes.valueOf(command.getOrElse(CommandTypes.UP.toString()).toUpperCase())
        final int resolvedCount = count.getOrElse(0)
        if (resolvedCount <= 0) {
            logger.quiet("Requested '${resolvedCount}' instances. Returning...")
            return
        }

        final String resolvedId = id.getOrElse(project.getName())
        final String resolvedNetwork = network.getOrNull()

        // 2.) Build `main` container
        MainContainer mainContainer = new MainContainer()
        for (final Closure<MainContainer> cnf : mainConfigs) {
            mainContainer = ConfigureUtil.configure(cnf, mainContainer)
        }

        requireNonNull(mainContainer.repository(), "'main' must have a valid repository defined")

        // 3.) Build `data` container which is not required to be defined as it will/can inherit properties from main.
        DataContainer dataContainer = new DataContainer()
        for (final Closure<DataContainer> cnf : dataConfigs) {
            dataContainer = ConfigureUtil.configure(cnf, dataContainer)
        }

        if (!dataContainer.repository()) {
            dataContainer.repository = mainContainer.repository()
            dataContainer.tag = mainContainer.tag()
        }

        // 4.) kick off worker(s) to do our processing in parallel
        for (int index = 1; index <= resolvedCount; index++) {

            // report will be filled out by worker and available once task execution has completed
            final SummaryReport summaryReport = new SummaryReport(resolvedCommand)
            reports.add(summaryReport)

            final WorkerMetaData workerObject = new WorkerMetaData(resolvedCommand, project, resolvedId, index, resolvedNetwork, mainContainer, dataContainer, summaryReport)
            final String hash = UUID.randomUUID().toString().md5()
            WorkerMetaDataCache.put(hash, workerObject)

            workerExecutor.submit(DockerWorker, { cfg ->
                cfg.setIsolationMode(IsolationMode.NONE)
                cfg.setParams(hash)
                cfg.setDisplayName(resolvedCommand.toString())
            })
        }
    }

    void main(final List<Closure<MainContainer>> mainConfigList) {
        mainConfigList?.each { cfg -> main(cfg) }
    }

    void main(final Closure<MainContainer> mainConfig) {
        if (mainConfig) { mainConfigs.add(mainConfig) }
    }

    void data(final List<Closure<DataContainer>> dataConfigList) {
        dataConfigList?.each { cfg ->  data(cfg) }
    }

    void data(final Closure<DataContainer> dataConfig) {
        if (dataConfig) { dataConfigs.add(dataConfig) }
    }

    List<SummaryReport> reports() {
        reports
    }
}
