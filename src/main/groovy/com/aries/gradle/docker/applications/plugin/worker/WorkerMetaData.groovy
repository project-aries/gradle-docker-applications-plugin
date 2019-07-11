package com.aries.gradle.docker.applications.plugin.worker

import com.aries.gradle.docker.applications.plugin.domain.CommandTypes
import com.aries.gradle.docker.applications.plugin.domain.DataContainer
import com.aries.gradle.docker.applications.plugin.domain.FrontEnd
import com.aries.gradle.docker.applications.plugin.domain.MainContainer
import com.aries.gradle.docker.applications.plugin.utils.Pair
import com.aries.gradle.docker.applications.plugin.report.SummaryReport
import com.aries.gradle.docker.applications.plugin.report.SummaryReportCache
import org.gradle.api.GradleException
import org.gradle.api.Project

import javax.annotation.Nullable

import static com.aries.gradle.docker.applications.plugin.report.SummaryReportCache.create
import static com.aries.gradle.docker.applications.plugin.utils.NginxUtils.buildConfig
import static java.util.Objects.requireNonNull

/**
 *
 * MetaData for the eventual worker thread to pick up and use for execution.
 *
 */
class WorkerMetaData {

    final String appName
    final CommandTypes command
    final Project project
    final String id
    final String network
    final MainContainer mainContainer
    final DataContainer dataContainer
    final FrontEnd frontEnd
    final SummaryReport summaryReport

    final String mainId
    final String dataId

    WorkerMetaData(final String appName,
                   final CommandTypes command,
                   final Project project,
                   final String id,
                   @Nullable final Integer index,
                   @Nullable final String network,
                   final MainContainer mainContainer,
                   final DataContainer dataContainer,
                   @Nullable final FrontEnd frontEnd) {

        this.appName = requireNonNull(appName)
        this.command = requireNonNull(command)
        this.project = requireNonNull(project)
        this.id = requireNonNull(id)
        this.network = network
        this.mainContainer = requireNonNull(mainContainer)
        this.dataContainer = requireNonNull(dataContainer)
        this.frontEnd = frontEnd

        // we only care to keep track of SummaryReports if they came from an UP request
        this.summaryReport = (command == CommandTypes.UP ? create(appName) : create(null))
        this.summaryReport.commandType = this.command

        final String appender = index != null ? "-${index}" : ""
        this.mainId = "${id}${appender}"
        this.dataId = "${id}-data${appender}"
    }

    /**
     * Build a WorkerMetaData object from the combined properties of THIS object
     * and the `FrontEnd` object lazily. This is ONLY required if requested by
     * the user which is why this is done at runtime and not AOT.
     *
     * @return WorkerMetaData based off of `FrontEnd` or NULL if `FrontEnd` was not defined.
     */
    WorkerMetaData frontEndMetaData() {
        if (frontEnd) {

            final List<Proxy> proxies = frontEnd.frontContainer.proxies()
            final Set<SummaryReport> reports = SummaryReportCache.matching(appName)
            final Pair<String, List<String>> config = buildConfig(proxies, reports)
            if (!config.empty()) {

                // noticed that on macs sometimes the directory creation returns false
                // yet the directory was actually created. to account for this we will
                // do a double check with a pause in between to account for potentially
                // slower disks.
                final File buildDir = project.getBuildDir()
                if (!buildDir.exists()) {
                    if (!buildDir.mkdirs()) {
                        sleep 1
                        if (!buildDir.exists()) {
                            throw new GradleException("Could not successfully of directory @ ${buildDir.path}")
                        }
                    }
                }

                final File nginxConf = new File("$buildDir/nginx.conf")
                nginxConf.withWriter('UTF-8') { writer ->
                    writer.println(config.left())
                    nginxConf
                }

                final List<Integer> localExposedPorts = new ArrayList<>()
                for(final String portBinding : config.right()) {
                    int exposePort = Integer.valueOf(portBinding.split(":")[1])
                    localExposedPorts.add(exposePort)
                }

                final MainContainer frontMainContainer = MainContainer.buildFrom({ cfg ->
                    cfg.repository = frontEnd.frontContainer.repository()
                    cfg.tag = frontEnd.frontContainer.tag()
                    cfg.create { conf ->
                        conf.portBindings = config.right()
                        conf.exposePorts('tcp', localExposedPorts)
                    }

                    cfg.files { conf ->
                        conf.withFile("${nginxConf.path}", '/etc/nginx/') // demo with strings
                    }
                })
                final DataContainer frontDataContainer = DataContainer.buildFrom(frontMainContainer, null)

                final WorkerMetaData frontMetaData = new WorkerMetaData(this.appName,
                    this.command,
                    this.project,
                    this.id + '-front',
                    null,
                    null,
                    frontMainContainer,
                    frontDataContainer,
                    null)

                return frontMetaData
            }
        }

        return null
    }
}
