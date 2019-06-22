package com.aries.gradle.docker.applications.plugin.tasks

import com.aries.gradle.docker.applications.plugin.report.SummaryReport
import com.aries.gradle.docker.applications.plugin.report.SummaryReportCache
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction

import javax.annotation.Nullable

/**
 *
 * Collects and houses all reports for downstream querying and assessing.
 *
 */
class SummaryReportCollector extends DefaultTask {

    @Input
    @Optional
    final Property<String> appNamesMatching = project.objects.property(String)

    @Internal
    private final String appName

    SummaryReportCollector() {
        this.appName = getGroup() ?: project.getName()
    }

    @TaskAction
    void execute() {
        logger.debug("Found ${reports().size()} reports...")
    }

    List<SummaryReport> reports(@Nullable final String matching = null) {
        SummaryReportCache.matching(appName, matching, appNamesMatching.getOrNull())
    }
}
