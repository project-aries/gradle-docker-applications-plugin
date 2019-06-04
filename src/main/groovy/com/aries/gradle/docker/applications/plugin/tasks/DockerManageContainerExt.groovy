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
import com.aries.gradle.docker.applications.plugin.worker.WorkerObject
import com.aries.gradle.docker.applications.plugin.worker.WorkerObjectCache
import org.gradle.api.DefaultTask
import org.gradle.api.Task
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.IsolationMode
import org.gradle.workers.WorkerExecutor

import javax.inject.Inject

import static java.util.Objects.requireNonNull

/**
 * Manage the lifecycle of dockerized application-container(s)
 */
class DockerManageContainerExt extends DefaultTask {

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

    @Input
    @Optional
    final Property<MainContainer> main = project.objects.property(MainContainer)

    @Input
    @Optional
    final Property<DataContainer> data = project.objects.property(DataContainer)

    @Internal
    private final List<SummaryReport> reports = []

    @Internal
    private final WorkerExecutor workerExecutor

    @Inject
    DockerManageContainerExt(final WorkerExecutor workerExecutor) {
        this.workerExecutor = workerExecutor
    }

    @TaskAction
    void execute() {

        // 1.) Initialize all properties and set defaults where necessary
        final CommandTypes resolvedCommand = CommandTypes.valueOf(command.getOrElse(CommandTypes.UP.toString()))
        final int resolvedCount = count.getOrElse(1)
        final String resolvedId = id.getOrElse(project.getName())
        final String resolvedNetwork = network.getOrNull()

        final MainContainer mainContainer = requireNonNull(main.getOrNull(), "'main' container must be defined")
        requireNonNull(mainContainer.repository(), "'main' must have a valid repository defined")

        final DataContainer dataContainer = data.getOrElse(new DataContainer())
        if (!dataContainer.repository()) {
            dataContainer.repository = mainContainer.repository()
            dataContainer.tag = mainContainer.tag()
        }

        // 2.) kick off worker to do our processing in parallel
        for (int index = 1; index <= resolvedCount; index++) {

            // report will be filled out by worker and available once task execution has completed
            final SummaryReport summaryReport = new SummaryReport()
            reports.add(summaryReport)

            final WorkerObject workerObject = new WorkerObject(resolvedCommand, project, resolvedId, index, resolvedNetwork, mainContainer, dataContainer, summaryReport)
            final String hash = UUID.randomUUID().toString().md5()
            WorkerObjectCache.put(hash, workerObject)

            workerExecutor.submit(DockerWorker, { cfg ->
                cfg.setIsolationMode(IsolationMode.NONE)
                cfg.setParams(hash)
                cfg.setDisplayName(resolvedCommand.toString())
            })
        }
    }

    Task main(final Closure<MainContainer> mataContainerConfig) {
        if (mataContainerConfig) {
            main.set(project.provider(mataContainerConfig))
        }
        this
    }

    Task data(final Closure<DataContainer> dataContainerConfig) {
        if (dataContainerConfig) {
            data.set(project.provider(dataContainerConfig))
        }
        this
    }

    List<SummaryReport> reports() {
        reports
    }
}
