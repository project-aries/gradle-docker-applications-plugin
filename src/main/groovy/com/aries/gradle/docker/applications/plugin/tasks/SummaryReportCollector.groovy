package com.aries.gradle.docker.applications.plugin.tasks

import com.aries.gradle.docker.applications.plugin.report.SummaryReport
import org.gradle.api.DefaultTask
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction

/**
 *
 * Collects and houses all reports for downstream querying and assessing.
 *
 */
class SummaryReportCollector extends DefaultTask {

    @Input
    @Optional
    final ListProperty<SummaryReport> reports = project.objects.listProperty(SummaryReport)

    SummaryReportCollector() {
        reports.empty()
    }

    @TaskAction
    void execute() {
        logger.debug("Found ${reports().size()} reports...")
    }

    List<SummaryReport> reports() {
        reports.getOrElse([])
    }
}
